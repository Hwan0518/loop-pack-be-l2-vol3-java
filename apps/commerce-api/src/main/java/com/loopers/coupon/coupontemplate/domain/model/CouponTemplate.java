package com.loopers.coupon.coupontemplate.domain.model;


import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
import com.loopers.coupon.coupontemplate.domain.service.discount.DiscountPolicy;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Objects;


@Getter
public class CouponTemplate {

	/**
	 * 쿠폰 템플릿
	 * - id: 쿠폰 템플릿 ID
	 * - name: 쿠폰 이름 (최대 100자)
	 * - type: 쿠폰 타입 (FIXED / RATE, 생성 후 불변)
	 * - value: 할인 값 (FIXED: 원화, RATE: 퍼센트)
	 * - minOrderAmount: 최소 주문 금액 (null이면 조건 없음)
	 * - expiredAt: 만료 일시
	 * - deletedAt: 삭제 일시 (Soft Delete)
	 */

	private final Long id;
	private String name;
	private final CouponType type;
	private final BigDecimal value;
	private final BigDecimal minOrderAmount;
	private LocalDateTime expiredAt;
	private ZonedDateTime deletedAt;


	// 생성자
	private CouponTemplate(Long id, String name, CouponType type, BigDecimal value,
		BigDecimal minOrderAmount, LocalDateTime expiredAt, ZonedDateTime deletedAt) {
		this.id = id;
		this.name = name;
		this.type = type;
		this.value = value;
		this.minOrderAmount = minOrderAmount;
		this.expiredAt = expiredAt;
		this.deletedAt = deletedAt;
	}


	/**
	 * 도메인 로직
	 * 1. 쿠폰 템플릿 생성
	 * 2. 쿠폰 템플릿 재생성 (Entity -> Model 매핑용도)
	 * 3. 쿠폰 이름 변경
	 * 4. 만료일 변경
	 * 5. 삭제 (soft delete)
	 * 6. 삭제 여부 확인
	 * 7. 할인 금액 계산
	 */

	// 1. 쿠폰 템플릿 생성 (유효성 검증 포함)
	public static CouponTemplate create(String name, CouponType type, BigDecimal value,
		BigDecimal minOrderAmount, LocalDateTime expiredAt) {

		// type null 검증
		Objects.requireNonNull(type, "type");

		// 이름 검증
		validateName(name);

		// 할인 값 검증 (타입별)
		validateValue(type, value);

		// 만료일 검증 (현재 시각 이후여야 함)
		validateExpiredAt(expiredAt);

		// FIXED 타입: minOrderAmount >= value 검증
		if (minOrderAmount != null && type == CouponType.FIXED) {
			validateFixedMinOrderAmount(value, minOrderAmount);
		}

		return new CouponTemplate(null, name, type, value, minOrderAmount, expiredAt, null);
	}


	// 2. 쿠폰 템플릿 재생성 (Entity -> Model 매핑용도)
	public static CouponTemplate reconstruct(Long id, String name, CouponType type, BigDecimal value,
		BigDecimal minOrderAmount, LocalDateTime expiredAt, ZonedDateTime deletedAt) {
		return new CouponTemplate(id, name, type, value, minOrderAmount, expiredAt, deletedAt);
	}


	// 3. 쿠폰 이름 변경
	public void changeName(String name) {
		validateName(name);
		this.name = name;
	}


	// 4. 만료일 변경
	public void changeExpiredAt(LocalDateTime expiredAt) {
		validateExpiredAt(expiredAt);
		this.expiredAt = expiredAt;
	}


	// 5. 삭제 (soft delete)
	public void delete() {
		this.deletedAt = ZonedDateTime.now();
	}


	// 6. 삭제 여부 확인
	public boolean isDeleted() {
		return this.deletedAt != null;
	}


	// 7. 할인 금액 계산 (전략 패턴으로 위임)
	public BigDecimal calculateDiscount(BigDecimal totalPrice) {
		DiscountPolicy policy = type.createPolicy(value);
		return policy.calculate(totalPrice);
	}


	/**
	 * private 메서드
	 * 1. 이름 검증
	 * 2. 할인 값 검증 (타입별)
	 * 3. 만료일 검증
	 * 4. FIXED 타입 최소 주문 금액 검증
	 */

	// 1. 이름 검증 (1~100자)
	private static void validateName(String name) {
		if (name == null || name.isBlank() || name.length() > 100) {
			throw new CoreException(ErrorType.INVALID_COUPON_NAME);
		}
	}


	// 2. 할인 값 검증 (타입별)
	private static void validateValue(CouponType type, BigDecimal value) {
		if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
			throw new CoreException(ErrorType.INVALID_COUPON_VALUE);
		}
		// RATE 타입: 1 <= value <= 100
		if (type == CouponType.RATE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
			throw new CoreException(ErrorType.INVALID_COUPON_VALUE);
		}
	}


	// 3. 만료일 검증 (현재 시각 이후여야 함)
	private static void validateExpiredAt(LocalDateTime expiredAt) {
		if (expiredAt == null || !expiredAt.isAfter(LocalDateTime.now())) {
			throw new CoreException(ErrorType.INVALID_COUPON_EXPIRED_AT);
		}
	}


	// 4. FIXED 타입: minOrderAmount >= value 검증
	private static void validateFixedMinOrderAmount(BigDecimal value, BigDecimal minOrderAmount) {
		if (minOrderAmount.compareTo(value) < 0) {
			throw new CoreException(ErrorType.COUPON_VALUE_EXCEEDS_MIN_ORDER_AMOUNT);
		}
	}

}
