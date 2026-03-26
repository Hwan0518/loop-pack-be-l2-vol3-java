package com.loopers.coupon.infrastructure.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


/**
 * 쿠폰 발급 INSERT용 경량 엔티티 (issued_coupon)
 */

@Entity
@Table(name = "issued_coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreamerIssuedCouponEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "coupon_template_id", nullable = false)
	private Long couponTemplateId;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;


	public static StreamerIssuedCouponEntity of(Long userId, Long couponTemplateId) {
		StreamerIssuedCouponEntity entity = new StreamerIssuedCouponEntity();
		entity.userId = userId;
		entity.couponTemplateId = couponTemplateId;
		entity.status = "AVAILABLE";
		entity.createdAt = LocalDateTime.now();
		entity.updatedAt = LocalDateTime.now();
		return entity;
	}

}
