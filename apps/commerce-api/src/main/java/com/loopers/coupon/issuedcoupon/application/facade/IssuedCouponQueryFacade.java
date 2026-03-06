package com.loopers.coupon.issuedcoupon.application.facade;


import com.loopers.coupon.coupontemplate.application.service.CouponTemplateQueryService;
import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.issuedcoupon.application.dto.out.AdminIssuedCouponOutDto;
import com.loopers.coupon.issuedcoupon.application.dto.out.IssuedCouponOutDto;
import com.loopers.coupon.issuedcoupon.application.service.IssuedCouponQueryService;
import com.loopers.coupon.issuedcoupon.domain.model.IssuedCoupon;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class IssuedCouponQueryFacade {

	// service
	private final IssuedCouponQueryService issuedCouponQueryService;
	private final CouponTemplateQueryService couponTemplateQueryService;


	/**
	 * 발급 쿠폰 조회 파사드
	 * 1. 내 쿠폰 목록 조회 (사용자)
	 * 2. 특정 쿠폰 템플릿의 발급 내역 조회 (관리자)
	 */

	// 1. 내 쿠폰 목록 조회 (사용자 인증 후 조회)
	@Transactional(readOnly = true)
	public List<IssuedCouponOutDto> getMyIssuedCoupons(String loginId, String password) {

		// 사용자 인증
		Long userId = issuedCouponQueryService.authenticate(loginId, password);

		// 발급 쿠폰 목록 조회
		List<IssuedCoupon> issuedCoupons = issuedCouponQueryService.getAllByUserId(userId);

		// 쿠폰 템플릿 일괄 조회 (중복 제거)
		List<Long> templateIds = issuedCoupons.stream()
			.map(IssuedCoupon::getCouponTemplateId)
			.distinct()
			.toList();

		Map<Long, CouponTemplate> templateMap = templateIds.stream()
			.collect(Collectors.toMap(
				id -> id,
				couponTemplateQueryService::getByIdIncludeDeleted
			));

		// 응답 DTO 변환
		return issuedCoupons.stream()
			.map(ic -> IssuedCouponOutDto.from(ic, templateMap.get(ic.getCouponTemplateId())))
			.toList();
	}


	// 2. 특정 쿠폰 템플릿의 발급 내역 조회 (관리자)
	@Transactional(readOnly = true)
	public List<AdminIssuedCouponOutDto> getIssuedCouponsByTemplateId(Long couponTemplateId) {

		// 쿠폰 템플릿 조회 (삭제 여부 무관)
		CouponTemplate template = couponTemplateQueryService.getByIdIncludeDeleted(couponTemplateId);

		// 해당 템플릿으로 발급된 쿠폰 목록 조회
		List<IssuedCoupon> issuedCoupons = issuedCouponQueryService.getAllByCouponTemplateId(couponTemplateId);

		// 응답 DTO 변환
		return issuedCoupons.stream()
			.map(ic -> AdminIssuedCouponOutDto.from(ic, template))
			.toList();
	}

}
