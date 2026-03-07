package com.loopers.coupon.issuedcoupon.interfaces.web.controller;


import com.loopers.coupon.issuedcoupon.application.dto.out.IssuedCouponOutDto;
import com.loopers.coupon.issuedcoupon.application.facade.IssuedCouponQueryFacade;
import com.loopers.coupon.issuedcoupon.interfaces.web.response.IssuedCouponResponse;
import com.loopers.support.common.auth.AuthenticationResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/v1/users/me/coupons")
@RequiredArgsConstructor
public class UserIssuedCouponQueryController {

	// facade
	private final IssuedCouponQueryFacade issuedCouponQueryFacade;
	// auth
	private final AuthenticationResolver authenticationResolver;


	/**
	 * 사용자 발급 쿠폰 조회 컨트롤러 (사용자 인증 필요)
	 * 1. 내 쿠폰 목록 조회
	 */

	// 1. 내 쿠폰 목록 조회
	@GetMapping
	public ResponseEntity<List<IssuedCouponResponse>> getMyIssuedCoupons(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password
	) {

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// 내 쿠폰 목록 조회
		List<IssuedCouponOutDto> outDtos = issuedCouponQueryFacade.getMyIssuedCoupons(userId);

		// 응답 변환
		List<IssuedCouponResponse> responses = outDtos.stream()
			.map(IssuedCouponResponse::from)
			.toList();

		// 200 OK 반환
		return ResponseEntity.ok(responses);
	}

}
