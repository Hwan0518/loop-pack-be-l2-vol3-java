package com.loopers.coupon.issuedcoupon.application.facade;


import com.loopers.coupon.coupontemplate.application.service.CouponTemplateQueryService;
import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.issuedcoupon.application.dto.out.CouponApplyResult;
import com.loopers.coupon.issuedcoupon.application.dto.out.CouponIssueOutDto;
import com.loopers.coupon.issuedcoupon.application.service.IssuedCouponCommandService;
import com.loopers.coupon.issuedcoupon.application.service.IssuedCouponQueryService;
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
	private final IssuedCouponQueryService issuedCouponQueryService;
	private final CouponTemplateQueryService couponTemplateQueryService;


	/**
	 * 발급 쿠폰 명령 파사드
	 * 1. 쿠폰 발급 (1차: 로컬 캐시, 2차: DB 존재 확인으로 중복 차단, DB 복합 유니크 제약이 데이터 무결성 보장)
	 * 2. 쿠폰 적용 (주문 BC -> Cross-BC 호출 전용)
	 */

	// 1. 쿠폰 발급 (1차: 로컬 캐시, 2차: DB 존재 확인으로 중복 차단, DB 복합 유니크 제약이 데이터 무결성 보장)
	@Transactional
	public CouponIssueOutDto issueCoupon(String loginId, String password, Long couponTemplateId) {

		// 사용자 인증
		Long userId = issuedCouponCommandService.authenticate(loginId, password);

		// 중복 발급 차단 (1차: 로컬 캐시 따닥 차단, 2차: DB 존재 확인으로 캐시 TTL 만료 후 재요청 방어)
		issuedCouponCommandService.validateNotDuplicateIssue(userId, couponTemplateId);

		// 쿠폰 템플릿 존재 + 활성 검증 (만료/삭제 시 예외)
		couponTemplateQueryService.getById(couponTemplateId);

		// 발급 쿠폰 생성 및 저장 (0.01% 레이스 시 DB 복합 유니크 제약이 안전망 — 500 → 클라이언트 재시도 → 캐시에서 중복 차단)
		IssuedCoupon issuedCoupon = IssuedCoupon.create(couponTemplateId, userId);
		IssuedCoupon saved = issuedCouponCommandService.save(issuedCoupon);
		return CouponIssueOutDto.from(saved);
	}


	// 2. 쿠폰 적용 (주문 BC에서 Cross-BC Port를 통해 호출 — 1:1 사용자 쿠폰이므로 동시성 경합 없음)
	public CouponApplyResult applyToCoupon(Long issuedCouponId, Long userId, BigDecimal totalPrice) {

		// 쿠폰 템플릿 조회 (삭제 여부 무관 — 만료 판단은 isExpired()에서 처리)
		IssuedCoupon issuedCoupon = issuedCouponQueryService.getById(issuedCouponId);
		CouponTemplate template = couponTemplateQueryService.getByIdIncludeDeleted(issuedCoupon.getCouponTemplateId());

		// 쿠폰 적용 (검증 + 상태 변경 + 할인 계산)
		return issuedCouponCommandService.applyToCoupon(issuedCouponId, userId, totalPrice, template);
	}

}
