package com.loopers.coupon.coupontemplate.interfaces.web.controller;


import com.loopers.coupon.coupontemplate.application.dto.out.AdminCouponTemplatePageOutDto;
import com.loopers.coupon.coupontemplate.application.facade.CouponTemplateQueryFacade;
import com.loopers.coupon.coupontemplate.interfaces.web.response.AdminCouponTemplatePageResponse;
import com.loopers.support.common.AdminHeaderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api-admin/v1/coupons")
@RequiredArgsConstructor
public class CouponTemplateAdminQueryController {

	// facade
	private final CouponTemplateQueryFacade couponTemplateQueryFacade;


	/**
	 * 쿠폰 템플릿 관리자 조회 컨트롤러 (LDAP 인증 필요)
	 * 1. 쿠폰 템플릿 목록 조회
	 */

	// 1. 쿠폰 템플릿 목록 조회 (페이지네이션)
	@GetMapping
	public ResponseEntity<AdminCouponTemplatePageResponse> getCouponTemplates(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// 쿠폰 템플릿 목록 조회
		AdminCouponTemplatePageOutDto result = couponTemplateQueryFacade.getCouponTemplates(page, size);

		// 응답 변환
		AdminCouponTemplatePageResponse response = AdminCouponTemplatePageResponse.from(result);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}

}
