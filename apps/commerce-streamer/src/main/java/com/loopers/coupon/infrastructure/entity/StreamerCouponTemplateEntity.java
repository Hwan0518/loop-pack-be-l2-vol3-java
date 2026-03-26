package com.loopers.coupon.infrastructure.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * maxQuantity 조회용 경량 엔티티 (coupon_template, 읽기 전용)
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

}
