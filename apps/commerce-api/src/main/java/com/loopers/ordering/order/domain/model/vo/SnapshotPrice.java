package com.loopers.ordering.order.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.math.BigDecimal;
import java.util.Objects;


/**
 * 주문 시점 상품 가격 스냅샷 값 객체
 * - value: 가격 값 (0 이상)
 */
public record SnapshotPrice(BigDecimal value) {


	/**
	 * 도메인 로직
	 * 1. 가격 스냅샷 생성
	 * 2. DB 복원용 가격 스냅샷 생성
	 */

	// 1. 가격 스냅샷 생성
	public static SnapshotPrice create(BigDecimal price) {

		// 유효성 검증
		validate(price);

		return new SnapshotPrice(price);
	}


	// 2. DB 복원용 가격 스냅샷 생성
	public static SnapshotPrice from(BigDecimal value) {
		return new SnapshotPrice(Objects.requireNonNull(value, "snapshotPrice value"));
	}


	/**
	 * private 메서드
	 * 1. 가격 형식 검증
	 */

	// 1. 가격 형식 검증
	private static void validate(BigDecimal price) {

		if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
			throw new CoreException(ErrorType.INVALID_SNAPSHOT_PRICE);
		}
	}

}
