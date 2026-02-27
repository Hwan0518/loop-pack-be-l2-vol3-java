package com.loopers.cart.cart.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.util.Objects;


/**
 * 장바구니 수량 값 객체
 * - value: 수량 값 (1 이상)
 */

public record Quantity(Long value) {


	/**
	 * 도메인 로직
	 * 1. 수량 생성
	 * 2. DB 복원용 수량 생성
	 * 3. 수량 추가
	 */

	// 1. 수량 생성
	public static Quantity create(Long value) {
		validate(value);
		return new Quantity(value);
	}


	// 2. DB 복원용 수량 생성
	public static Quantity from(Long value) {
		return new Quantity(Objects.requireNonNull(value, "quantity value"));
	}


	// 3. 수량 추가
	public Quantity add(Long amount) {
		validate(amount);
		return new Quantity(this.value + amount);
	}


	/**
	 * private 메서드
	 * 1. 수량 유효성 검증
	 */

	// 1. 수량 유효성 검증
	private static void validate(Long value) {
		if (value == null || value <= 0) {
			throw new CoreException(ErrorType.INVALID_QUANTITY);
		}
	}

}
