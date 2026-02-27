package com.loopers.user.user.domain.repository.vo;

/**
 * 페이지네이션 조건
 * - page: 페이지 번호
 * - size: 페이지 크기
 */
public record PageCriteria(
	int page,
	int size
) {
	public static PageCriteria of(int page, int size) {
		return new PageCriteria(page, size);
	}
}
