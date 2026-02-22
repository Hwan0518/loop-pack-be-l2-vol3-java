package com.loopers.catalog.brand.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;


/**
 * 브랜드 설명 값 객체
 * - value: 정규화된 브랜드 설명 값
 */

public record BrandDescription(String value) {

	private static final int MAX_LENGTH = 500;


	/**
	 * 도메인 로직
	 * 1. 브랜드 설명 생성
	 * 2. DB 복원용 브랜드 설명 생성
	 */

	// 1. 브랜드 설명 생성 (null 허용)
	public static BrandDescription create(String rawDescription) {

		// null 허용
		if (rawDescription == null) {
			return null;
		}

		// trim 후 검증
		String trimmed = rawDescription.trim();
		validate(trimmed);
		return new BrandDescription(trimmed);
	}


	// 2. DB 복원용 브랜드 설명 생성 (null 허용)
	public static BrandDescription from(String value) {
		return value != null ? new BrandDescription(value) : null;
	}


	/**
	 * private 메서드
	 * 1. 브랜드 설명 형식 검증
	 */

	// 1. 브랜드 설명 형식 검증
	private static void validate(String description) {

		if (description.length() > MAX_LENGTH) {
			throw new CoreException(ErrorType.INVALID_BRAND_DESCRIPTION);
		}
	}

}
