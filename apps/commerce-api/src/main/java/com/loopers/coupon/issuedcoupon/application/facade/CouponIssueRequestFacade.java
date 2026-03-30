package com.loopers.coupon.issuedcoupon.application.facade;


import com.loopers.coupon.issuedcoupon.application.dto.out.CouponIssueRequestOutDto;
import com.loopers.coupon.issuedcoupon.application.service.CouponIssueRequestCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


/**
 * 쿠폰 발급 요청 파사드
 * 1. 발급 요청 생성 (PENDING + Outbox 저장)
 * 2. 발급 요청 조회 (polling용)
 */

@Service
@RequiredArgsConstructor
public class CouponIssueRequestFacade {

	// service
	private final CouponIssueRequestCommandService couponIssueRequestCommandService;


	// 1. 발급 요청 생성
	public CouponIssueRequestOutDto createIssueRequest(Long userId, Long couponTemplateId, String requestId) {
		return couponIssueRequestCommandService.createIssueRequest(userId, couponTemplateId, requestId);
	}


	// 2. 발급 요청 조회 (polling용)
	public CouponIssueRequestOutDto getIssueRequest(String requestId, Long userId) {
		return couponIssueRequestCommandService.getIssueRequest(requestId, userId);
	}

}
