package com.loopers.catalog.brand.application.dto.out;


import java.util.List;


/**
 * 브랜드 관리자 페이지 조회 결과 DTO
 * - content: 브랜드 관리자 목록
 * - page: 페이지 번호 (0-based)
 * - size: 페이지 크기
 * - totalElements: 전체 요소 수
 */
public record AdminBrandPageOutDto(List<AdminBrandOutDto> content, int page, int size, long totalElements) {
}
