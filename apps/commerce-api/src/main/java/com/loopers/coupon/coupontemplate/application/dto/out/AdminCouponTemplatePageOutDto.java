package com.loopers.coupon.coupontemplate.application.dto.out;


import java.util.List;


/**
 * 관리자 쿠폰 템플릿 페이지 조회 결과 DTO
 * - content: 쿠폰 템플릿 목록
 * - page: 페이지 번호 (0-based)
 * - size: 페이지 크기
 * - totalElements: 전체 요소 수
 */
public record AdminCouponTemplatePageOutDto(List<AdminCouponTemplateOutDto> content, int page, int size, long totalElements) {
}
