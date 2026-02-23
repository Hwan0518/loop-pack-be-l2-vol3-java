package com.loopers.catalog.product.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.math.BigDecimal;
import java.util.Objects;


/**
 * 금액 값 객체
 * - value: 금액 값 (0 이상)
 */

public record Money(BigDecimal value) {


	/**
	 * 도메인 로직
	 * 1. 금액 생성
	 * 2. DB 복원용 금액 생성
	 */

	// 1. 금액 생성
	public static Money create(BigDecimal value) {
		validate(value);
		return new Money(value);
	}


	// 2. DB 복원용 금액 생성
	public static Money from(BigDecimal value) {
		return new Money(Objects.requireNonNull(value, "money value"));
	}


	/**
	 * private 메서드
	 * 1. 금액 검증
	 */

	// 1. 금액 검증
	private static void validate(BigDecimal value) {

		if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
			throw new CoreException(ErrorType.INVALID_PRODUCT_PRICE);
		}
	}

}
