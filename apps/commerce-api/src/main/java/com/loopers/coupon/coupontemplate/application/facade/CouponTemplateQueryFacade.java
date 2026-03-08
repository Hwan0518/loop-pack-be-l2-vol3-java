package com.loopers.coupon.coupontemplate.application.facade;


import com.loopers.coupon.coupontemplate.application.dto.out.AdminCouponTemplatePageOutDto;
import com.loopers.coupon.coupontemplate.application.service.CouponTemplateQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class CouponTemplateQueryFacade {

	// service
	private final CouponTemplateQueryService couponTemplateQueryService;


	/**
	 * 쿠폰 템플릿 조회 파사드
	 * 1. 쿠폰 템플릿 목록 페이지 조회 (관리자)
	 */

	// 1. 쿠폰 템플릿 목록 페이지 조회 (삭제되지 않은 것만)
	@Transactional(readOnly = true)
	public AdminCouponTemplatePageOutDto getCouponTemplates(int page, int size) {
		return couponTemplateQueryService.getAll(page, size);
	}

}
