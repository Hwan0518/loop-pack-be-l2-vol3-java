package com.loopers.catalog.product.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.util.Objects;


/**
 * 재고 값 객체
 * - value: 재고 수량 (0 이상)
 */

public record Stock(Long value) {


	/**
	 * 도메인 로직
	 * 1. 재고 생성
	 * 2. DB 복원용 재고 생성
	 * 3. 재고 차감
	 */

	// 1. 재고 생성
	public static Stock create(Long value) {
		validate(value);
		return new Stock(value);
	}


	// 2. DB 복원용 재고 생성
	public static Stock from(Long value) {
		return new Stock(Objects.requireNonNull(value, "stock value"));
	}


	// 3. 재고 차감
	public Stock decrease(Long quantity) {

		// 차감 수량 검증
		if (quantity == null || quantity <= 0) {
			throw new CoreException(ErrorType.INVALID_PRODUCT_STOCK);
		}

		// 재고 부족 검증
		if (this.value < quantity) {
			throw new CoreException(ErrorType.PRODUCT_OUT_OF_STOCK);
		}

		return new Stock(this.value - quantity);
	}


	/**
	 * private 메서드
	 * 1. 재고 검증
	 */

	// 1. 재고 검증
	private static void validate(Long value) {

		if (value == null || value < 0) {
			throw new CoreException(ErrorType.INVALID_PRODUCT_STOCK);
		}
	}

}
