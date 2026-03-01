package com.loopers.catalog.brand.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.util.Objects;


/**
 * 브랜드명 값 객체
 * - value: 정규화된 브랜드명 값
 */

public record BrandName(String value) {

	private static final int MAX_LENGTH = 100;


	/**
	 * 도메인 로직
	 * 1. 브랜드명 생성
	 * 2. DB 복원용 브랜드명 생성
	 * 3. 브랜드명 정규화
	 */

	// 1. 브랜드명 생성
	public static BrandName create(String rawName) {
		String normalized = normalize(rawName);
		validate(normalized);
		return new BrandName(normalized);
	}


	// 2. DB 복원용 브랜드명 생성
	public static BrandName from(String value) {
		return new BrandName(Objects.requireNonNull(value, "brandName value"));
	}


	// 3. 브랜드명 정규화
	public static String normalize(String name) {
		if (name == null) {
			return null;
		}
		String trimmed = name.trim();
		return trimmed.isBlank() ? null : trimmed;
	}


	/**
	 * private 메서드
	 * 1. 브랜드명 형식 검증
	 */

	// 1. 브랜드명 형식 검증
	private static void validate(String name) {

		if (name == null || name.length() > MAX_LENGTH) {
			throw new CoreException(ErrorType.INVALID_BRAND_NAME);
		}
	}

}
