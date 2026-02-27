package com.loopers.user.user.domain.repository.vo;

import java.util.List;

/**
 * 페이지네이션 결과
 * - content: 조회 결과 목록
 * - page: 페이지 번호
 * - size: 페이지 크기
 * - totalElements: 전체 요소 수
 */
public record PageResult<T>(
	List<T> content,
	int page,
	int size,
	long totalElements
) {
	public static <T> PageResult<T> of(List<T> content, int page, int size, long totalElements) {
		return new PageResult<>(content, page, size, totalElements);
	}
}
