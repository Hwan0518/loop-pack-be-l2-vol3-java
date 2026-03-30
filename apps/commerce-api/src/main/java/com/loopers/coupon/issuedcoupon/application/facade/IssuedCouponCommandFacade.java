package com.loopers.coupon.issuedcoupon.application.facade;


import com.loopers.coupon.coupontemplate.application.service.CouponTemplateQueryService;
import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.issuedcoupon.application.dto.out.CouponApplyResult;
import com.loopers.coupon.issuedcoupon.application.service.IssuedCouponCommandService;
import com.loopers.coupon.issuedcoupon.domain.model.IssuedCoupon;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;


@Service
@RequiredArgsConstructor
public class IssuedCouponCommandFacade {

	// service
	private final IssuedCouponCommandService issuedCouponCommandService;
	private final CouponTemplateQueryService couponTemplateQueryService;


	/**
	 * 발급 쿠폰 명령 파사드
	 * 1. 쿠폰 적용 (Cross-BC 전용 — ACL에서 호출)
	 * 2. 쿠폰 복원 (Cross-BC 전용 — ACL에서 호출, 보상 트랜잭션)
	 */

	// 1. 쿠폰 적용 (Cross-BC 전용 — ACL에서 호출, 비관적 락으로 동시 사용 방지)
	@Transactional
	public CouponApplyResult applyToCoupon(Long issuedCouponId, Long userId, BigDecimal totalPrice) {

		// 발급 쿠폰 조회 (비관적 쓰기 락 — 1차 캐시 오염 방지를 위해 FOR UPDATE가 첫 번째 조회)
		IssuedCoupon issuedCoupon = issuedCouponCommandService.getByIdForUpdate(issuedCouponId);

		// 쿠폰 템플릿 조회 (삭제 여부 무관 — 만료 판단은 isExpired()에서 처리)
		CouponTemplate template = couponTemplateQueryService.getByIdIncludeDeleted(issuedCoupon.getCouponTemplateId());

		// 쿠폰 적용 (검증 + 상태 변경 + 할인 계산)
		return issuedCouponCommandService.applyToCoupon(issuedCoupon, userId, totalPrice, template);
	}


	// 2. 쿠폰 복원 (Cross-BC 전용 — ACL에서 호출, 보상 트랜잭션 — USED → AVAILABLE)
	@Transactional
	public void restoreCoupon(Long issuedCouponId) {
		issuedCouponCommandService.restoreCoupon(issuedCouponId);
	}

}
