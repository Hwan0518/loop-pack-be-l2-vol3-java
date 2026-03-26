package com.loopers.coupon.infrastructure.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;


/**
 * 쿠폰 템플릿 조회용 경량 엔티티 (coupon_template, 읽기 전용)
 * - maxQuantity: 최대 발급 수량 (null = 무제한)
 * - expiredAt: 만료 일시
 * - deletedAt: 삭제 일시 (soft delete)
 */

@Entity
@Table(name = "coupon_template")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreamerCouponTemplateEntity {

	@Id
	private Long id;

	@Column(name = "max_quantity")
	private Integer maxQuantity;

	@Column(name = "expired_at")
	private LocalDateTime expiredAt;

	@Column(name = "deleted_at")
	private ZonedDateTime deletedAt;


	// 삭제 여부
	public boolean isDeleted() {
		return deletedAt != null;
	}

	// 만료 여부
	public boolean isExpired() {
		return expiredAt != null && expiredAt.isBefore(LocalDateTime.now());
	}

}
