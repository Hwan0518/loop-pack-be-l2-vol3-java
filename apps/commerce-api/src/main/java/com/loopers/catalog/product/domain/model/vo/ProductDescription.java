package com.loopers.catalog.product.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;


/**
 * 상품 설명 값 객체
 * - value: 상품 설명 (nullable, 최대 1000자)
 */

public record ProductDescription(String value) {

	private static final int MAX_LENGTH = 1000;


	/**
	 * 도메인 로직
	 * 1. 상품 설명 생성
	 * 2. DB 복원용 상품 설명 생성
	 * 3. 상품 설명 정규화
	 */

	// 1. 상품 설명 생성
	public static ProductDescription create(String rawDescription) {

		// null인 경우 null 반환
		String normalized = normalize(rawDescription);
		if (normalized == null) {
			return null;
		}

		// 검증
		validate(normalized);
		return new ProductDescription(normalized);
	}


	// 2. DB 복원용 상품 설명 생성
	public static ProductDescription from(String value) {

		if (value == null) {
			return null;
		}
		return new ProductDescription(value);
	}


	// 3. 상품 설명 정규화
	public static String normalize(String description) {
		if (description == null) {
			return null;
		}
		String trimmed = description.trim();
		return trimmed.isBlank() ? null : trimmed;
	}


	/**
	 * private 메서드
	 * 1. 상품 설명 형식 검증
	 */

	// 1. 상품 설명 형식 검증
	private static void validate(String description) {

		if (description.length() > MAX_LENGTH) {
			throw new CoreException(ErrorType.INVALID_PRODUCT_DESCRIPTION);
		}
	}

}
