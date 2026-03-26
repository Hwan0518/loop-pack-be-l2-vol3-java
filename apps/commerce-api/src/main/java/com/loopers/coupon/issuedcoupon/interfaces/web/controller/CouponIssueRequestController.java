package com.loopers.coupon.issuedcoupon.interfaces.web.controller;


import com.loopers.coupon.issuedcoupon.application.dto.out.CouponIssueRequestOutDto;
import com.loopers.coupon.issuedcoupon.application.service.CouponIssueRequestCommandService;
import com.loopers.coupon.issuedcoupon.interfaces.web.request.CouponIssueRequestCreateRequest;
import com.loopers.coupon.issuedcoupon.interfaces.web.response.CouponIssueRequestResponse;
import com.loopers.support.common.auth.AuthenticationResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/**
 * 쿠폰 발급 요청 컨트롤러 (Kafka 비동기 처리)
 * 1. 발급 요청 (202 Accepted)
 * 2. 발급 요청 결과 조회 (polling)
 */

@RestController
@RequestMapping("/api/v1/coupon-issue-requests")
@RequiredArgsConstructor
public class CouponIssueRequestController {

	// service
	private final CouponIssueRequestCommandService couponIssueRequestCommandService;
	// auth
	private final AuthenticationResolver authenticationResolver;


	// 1. 쿠폰 발급 요청 (202 Accepted)
	@PostMapping
	public ResponseEntity<CouponIssueRequestResponse> createIssueRequest(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@Valid @RequestBody CouponIssueRequestCreateRequest request
	) {

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// 발급 요청 생성
		CouponIssueRequestOutDto outDto = couponIssueRequestCommandService.createIssueRequest(
			userId, request.couponTemplateId(), request.requestId());

		// 202 Accepted (requestId 포함)
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(CouponIssueRequestResponse.from(outDto));
	}


	// 2. 발급 요청 결과 조회 (polling)
	@GetMapping("/{requestId}")
	public ResponseEntity<CouponIssueRequestResponse> getIssueRequest(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@PathVariable String requestId
	) {

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// 결과 조회
		CouponIssueRequestOutDto outDto = couponIssueRequestCommandService.getIssueRequest(requestId, userId);

		// 200 OK
		return ResponseEntity.ok(CouponIssueRequestResponse.from(outDto));
	}

}
