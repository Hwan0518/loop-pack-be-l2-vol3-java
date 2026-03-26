package com.loopers.coupon.coupontemplate.infrastructure.entity;


import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
import com.loopers.domain.SoftDeleteBaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * 쿠폰 템플릿 엔티티
 * - name: 쿠폰 이름 (최대 100자)
 * - type: 쿠폰 타입 (FIXED / RATE)
 * - value: 할인 값 (FIXED: 원화, RATE: 퍼센트)
 * - minOrderAmount: 최소 주문 금액 (null이면 조건 없음)
 * - expiredAt: 만료 일시
 */
@Entity
@Table(name = "coupon_template", indexes = {
	// 활성 쿠폰 템플릿 목록: WHERE deleted_at IS NULL
	@Index(name = "idx_coupon_template_deleted", columnList = "deleted_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponTemplateEntity extends SoftDeleteBaseEntity {

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false)
	private CouponType type;

	@Column(name = "value", nullable = false, precision = 15, scale = 2)
	private BigDecimal value;

	@Column(name = "min_order_amount", precision = 15, scale = 2)
	private BigDecimal minOrderAmount;

	@Column(name = "max_quantity")
	private Integer maxQuantity;

	@Column(name = "expired_at", nullable = false)
	private LocalDateTime expiredAt;


	private CouponTemplateEntity(Long id, String name, CouponType type, BigDecimal value,
		BigDecimal minOrderAmount, Integer maxQuantity, LocalDateTime expiredAt) {
		super(id);
		this.name = name;
		this.type = type;
		this.value = value;
		this.minOrderAmount = minOrderAmount;
		this.maxQuantity = maxQuantity;
		this.expiredAt = expiredAt;
	}


	// DB 복원용 (id 포함)
	public static CouponTemplateEntity of(Long id, String name, CouponType type, BigDecimal value,
		BigDecimal minOrderAmount, Integer maxQuantity, LocalDateTime expiredAt) {
		return new CouponTemplateEntity(id, name, type, value, minOrderAmount, maxQuantity, expiredAt);
	}

	// 신규 생성용 (id = null)
	public static CouponTemplateEntity of(String name, CouponType type, BigDecimal value,
		BigDecimal minOrderAmount, Integer maxQuantity, LocalDateTime expiredAt) {
		return of(null, name, type, value, minOrderAmount, maxQuantity, expiredAt);
	}

}
