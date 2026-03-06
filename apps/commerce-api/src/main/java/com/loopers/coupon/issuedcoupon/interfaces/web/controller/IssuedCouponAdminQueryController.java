package com.loopers.coupon.issuedcoupon.interfaces.web.controller;


import com.loopers.coupon.issuedcoupon.application.dto.out.AdminIssuedCouponOutDto;
import com.loopers.coupon.issuedcoupon.application.facade.IssuedCouponQueryFacade;
import com.loopers.coupon.issuedcoupon.interfaces.web.response.AdminIssuedCouponResponse;
import com.loopers.support.common.AdminHeaderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api-admin/v1/coupons")
@RequiredArgsConstructor
public class IssuedCouponAdminQueryController {

	// facade
	private final IssuedCouponQueryFacade issuedCouponQueryFacade;


	/**
	 * 발급 쿠폰 관리자 조회 컨트롤러 (LDAP 인증 필요)
	 * 1. 특정 쿠폰 템플릿의 발급 내역 조회
	 */

	// 1. 특정 쿠폰 템플릿의 발급 내역 조회
	@GetMapping("/{couponId}/issues")
	public ResponseEntity<List<AdminIssuedCouponResponse>> getIssuedCoupons(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@PathVariable Long couponId
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// 발급 내역 조회
		List<AdminIssuedCouponOutDto> outDtos = issuedCouponQueryFacade.getIssuedCouponsByTemplateId(couponId);

		// 응답 변환
		List<AdminIssuedCouponResponse> responses = outDtos.stream()
			.map(AdminIssuedCouponResponse::from)
			.toList();

		// 200 OK 반환
		return ResponseEntity.ok(responses);
	}

}
