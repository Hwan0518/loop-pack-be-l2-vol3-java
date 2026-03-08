package com.loopers.coupon.issuedcoupon.interfaces.web.controller;


import com.loopers.coupon.issuedcoupon.application.dto.out.CouponIssueOutDto;
import com.loopers.coupon.issuedcoupon.application.facade.IssuedCouponCommandFacade;
import com.loopers.coupon.issuedcoupon.interfaces.web.response.IssuedCouponIssueResponse;
import com.loopers.support.common.auth.AuthenticationResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class IssuedCouponCommandController {

	// facade
	private final IssuedCouponCommandFacade issuedCouponCommandFacade;
	// auth
	private final AuthenticationResolver authenticationResolver;


	/**
	 * 쿠폰 발급 명령 컨트롤러 (사용자 인증 필요)
	 * 1. 쿠폰 발급
	 */

	// 1. 쿠폰 발급
	@PostMapping("/{couponId}/issue")
	public ResponseEntity<IssuedCouponIssueResponse> issueCoupon(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@PathVariable Long couponId
	) {

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// 쿠폰 발급
		CouponIssueOutDto outDto = issuedCouponCommandFacade.issueCoupon(userId, couponId);

		// 201 Created (발급된 쿠폰 ID 반환)
		return ResponseEntity.status(HttpStatus.CREATED).body(IssuedCouponIssueResponse.from(outDto));
	}

}
