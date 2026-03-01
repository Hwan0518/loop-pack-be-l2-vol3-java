package com.loopers.catalog.product.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.util.Objects;


/**
 * 상품명 값 객체
 * - value: 정규화된 상품명 값
 */

public record ProductName(String value) {

	private static final int MAX_LENGTH = 200;


	/**
	 * 도메인 로직
	 * 1. 상품명 생성
	 * 2. DB 복원용 상품명 생성
	 * 3. 상품명 정규화
	 */

	// 1. 상품명 생성
	public static ProductName create(String rawName) {
		String normalized = normalize(rawName);
		validate(normalized);
		return new ProductName(normalized);
	}


	// 2. DB 복원용 상품명 생성
	public static ProductName from(String value) {
		return new ProductName(Objects.requireNonNull(value, "productName value"));
	}


	// 3. 상품명 정규화
	public static String normalize(String name) {
		if (name == null) {
			return null;
		}
		String trimmed = name.trim();
		return trimmed.isBlank() ? null : trimmed;
	}


	/**
	 * private 메서드
	 * 1. 상품명 형식 검증
	 */

	// 1. 상품명 형식 검증
	private static void validate(String name) {

		if (name == null || name.length() > MAX_LENGTH) {
			throw new CoreException(ErrorType.INVALID_PRODUCT_NAME);
		}
	}

}
