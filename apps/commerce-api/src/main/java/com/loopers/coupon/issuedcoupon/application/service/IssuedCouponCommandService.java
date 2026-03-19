package com.loopers.coupon.issuedcoupon.application.service;


import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.issuedcoupon.application.dto.out.CouponApplyResult;
import com.loopers.coupon.issuedcoupon.application.port.out.cache.CouponIssueDuplicateGuard;
import com.loopers.coupon.issuedcoupon.domain.model.IssuedCoupon;
import com.loopers.coupon.issuedcoupon.domain.model.enums.IssuedCouponStatus;
import com.loopers.coupon.issuedcoupon.domain.repository.IssuedCouponCommandRepository;
import com.loopers.coupon.issuedcoupon.domain.repository.IssuedCouponQueryRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;


@Service
@RequiredArgsConstructor
public class IssuedCouponCommandService {

	// repository
	private final IssuedCouponCommandRepository issuedCouponCommandRepository;
	private final IssuedCouponQueryRepository issuedCouponQueryRepository;
	// cache
	private final CouponIssueDuplicateGuard couponIssueDuplicateGuard;


	/**
	 * 발급 쿠폰 명령 서비스
	 * 1. 중복 발급 차단 (1차: 로컬 캐시, 2차: DB 존재 확인)
	 * 2. 발급 락 해제 (발급 실패 시 캐시 점유 해제)
	 * 3. 발급 쿠폰 저장
	 * 4. 발급 쿠폰 조회 (비관적 쓰기 락)
	 * 5. 쿠폰 적용 (주문 BC에서 호출)
	 * 6. 쿠폰 복원 (보상 트랜잭션 — 주문 만료 시 USED → AVAILABLE)
	 */

	// 1. 중복 발급 차단 (1차: 로컬 캐시 — 따닥 차단, 2차: DB 존재 확인 — 캐시 TTL 만료 후 재요청 방어)
	public void validateNotDuplicateIssue(Long userId, Long couponTemplateId) {

		// 1차: 로컬 캐시 (동일 JVM 내 따닥 차단)
		if (!couponIssueDuplicateGuard.tryAcquire(userId, couponTemplateId)) {
			throw new CoreException(ErrorType.COUPON_ISSUE_DUPLICATED);
		}

		// 2차: DB 존재 확인 (캐시 TTL 만료 후 재요청 방어)
		if (issuedCouponQueryRepository.existsByUserIdAndCouponTemplateId(userId, couponTemplateId)) {
			throw new CoreException(ErrorType.COUPON_ISSUE_DUPLICATED);
		}
	}


	// 2. 발급 락 해제 (발급 실패 시 캐시 점유 해제 — 실제 발급되지 않은 요청이 캐시에 남아 차단되는 것을 방지)
	public void releaseIssueLock(Long userId, Long couponTemplateId) {
		couponIssueDuplicateGuard.release(userId, couponTemplateId);
	}


	// 3. 발급 쿠폰 저장
	@Transactional
	public IssuedCoupon save(IssuedCoupon issuedCoupon) {
		return issuedCouponCommandRepository.save(issuedCoupon);
	}


	// 4. 발급 쿠폰 조회 (비관적 쓰기 락 — 동일 사용자 멀티 디바이스 동시 주문 시 이중 사용 방지)
	@Transactional
	public IssuedCoupon getByIdForUpdate(Long issuedCouponId) {
		return issuedCouponQueryRepository.findByIdForUpdate(issuedCouponId)
			.orElseThrow(() -> new CoreException(ErrorType.ISSUED_COUPON_NOT_FOUND));
	}


	// 5. 쿠폰 적용 (Facade에서 비관적 락으로 조회한 쿠폰을 전달받아 검증 + 상태 변경 + 할인 계산)
	@Transactional
	public CouponApplyResult applyToCoupon(IssuedCoupon issuedCoupon, Long userId, BigDecimal totalPrice,
		CouponTemplate template) {

		// userId 검증 (소유자 확인)
		if (!issuedCoupon.getUserId().equals(userId)) {
			throw new CoreException(ErrorType.COUPON_NOT_OWNED_BY_USER);
		}

		// 이미 사용된 쿠폰 검증
		if (issuedCoupon.getStatus() == IssuedCouponStatus.USED) {
			throw new CoreException(ErrorType.COUPON_ALREADY_USED);
		}

		// 만료 여부 검증 (동적 계산)
		if (issuedCoupon.isExpired(template)) {
			throw new CoreException(ErrorType.COUPON_EXPIRED);
		}

		// 최소 주문 금액 검증
		if (template.getMinOrderAmount() != null && totalPrice.compareTo(template.getMinOrderAmount()) < 0) {
			throw new CoreException(ErrorType.COUPON_MIN_ORDER_AMOUNT_NOT_MET);
		}

		// 할인 금액 계산
		BigDecimal discountAmount = template.calculateDiscount(totalPrice);

		// 쿠폰 상태 USED로 변경 후 저장
		issuedCoupon.use();
		issuedCouponCommandRepository.save(issuedCoupon);

		// 쿠폰 적용 결과 반환
		return new CouponApplyResult(
			issuedCoupon.getId(),
			discountAmount,
			template.getName(),
			template.getType().name(),
			template.getValue()
		);
	}


	// 6. 쿠폰 복원 (보상 트랜잭션 — 주문 만료 시 USED → AVAILABLE, 비관적 락으로 동시성 방어)
	@Transactional
	public void restoreCoupon(Long issuedCouponId) {

		// 발급 쿠폰 조회 (비관적 쓰기 락 — 동시 사용/복원 경합 방지)
		IssuedCoupon issuedCoupon = issuedCouponQueryRepository.findByIdForUpdate(issuedCouponId)
			.orElseThrow(() -> new CoreException(ErrorType.ISSUED_COUPON_NOT_FOUND));

		// 쿠폰 복원 (도메인 로직 — USED → AVAILABLE)
		issuedCoupon.restore();

		// 저장
		issuedCouponCommandRepository.save(issuedCoupon);
	}

}
