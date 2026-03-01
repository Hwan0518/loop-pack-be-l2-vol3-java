package com.loopers.ordering.order.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.util.Objects;


/**
 * 주문 시점 상품명 스냅샷 값 객체
 * - value: 정규화된 상품명 스냅샷 값
 */
public record SnapshotName(String value) {

	private static final int MAX_LENGTH = 200;


	/**
	 * 도메인 로직
	 * 1. 상품명 스냅샷 생성
	 * 2. DB 복원용 상품명 스냅샷 생성
	 */

	// 1. 상품명 스냅샷 생성
	public static SnapshotName create(String rawName) {

		// 정규화
		String normalized = normalize(rawName);

		// 유효성 검증
		validate(normalized);

		return new SnapshotName(normalized);
	}


	// 2. DB 복원용 상품명 스냅샷 생성
	public static SnapshotName from(String value) {
		return new SnapshotName(Objects.requireNonNull(value, "snapshotName value"));
	}


	/**
	 * private 메서드
	 * 1. 상품명 정규화
	 * 2. 상품명 형식 검증
	 */

	// 1. 상품명 정규화
	private static String normalize(String name) {
		if (name == null) {
			return null;
		}
		String trimmed = name.trim();
		return trimmed.isBlank() ? null : trimmed;
	}


	// 2. 상품명 형식 검증
	private static void validate(String name) {

		if (name == null || name.length() > MAX_LENGTH) {
			throw new CoreException(ErrorType.INVALID_SNAPSHOT_NAME);
		}
	}

}
