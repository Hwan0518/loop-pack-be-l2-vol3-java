package com.loopers.coupon.coupontemplate.interfaces.web.response;


import com.loopers.coupon.coupontemplate.application.dto.out.AdminCouponTemplatePageOutDto;

import java.util.List;


/**
 * 쿠폰 템플릿 관리자 페이지 응답
 * - content: 쿠폰 템플릿 목록
 * - page: 페이지 번호 (0-based)
 * - size: 페이지 크기
 * - totalElements: 전체 요소 수
 */
public record AdminCouponTemplatePageResponse(
	List<AdminCouponTemplateResponse> content,
	int page,
	int size,
	long totalElements
) {

	// 1. AdminCouponTemplatePageOutDto를 컨트롤러 응답 객체로 변환
	public static AdminCouponTemplatePageResponse from(AdminCouponTemplatePageOutDto outDto) {
		return new AdminCouponTemplatePageResponse(
			outDto.content().stream().map(AdminCouponTemplateResponse::from).toList(),
			outDto.page(),
			outDto.size(),
			outDto.totalElements()
		);
	}

}
