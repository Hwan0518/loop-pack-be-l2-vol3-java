package com.loopers.coupon.issuedcoupon.infrastructure.mapper;


import com.loopers.coupon.issuedcoupon.domain.model.IssuedCoupon;
import com.loopers.coupon.issuedcoupon.infrastructure.entity.IssuedCouponEntity;
import org.springframework.stereotype.Component;


/**
 * 발급된 쿠폰 엔티티 매퍼
 * 1. Domain -> Entity 변환
 * 2. Entity -> Domain 변환
 */
@Component
public class IssuedCouponEntityMapper {

	// 1. Domain -> Entity 변환
	public IssuedCouponEntity toEntity(IssuedCoupon issuedCoupon) {
		return IssuedCouponEntity.of(
			issuedCoupon.getId(),
			issuedCoupon.getCouponTemplateId(),
			issuedCoupon.getUserId(),
			issuedCoupon.getStatus()
		);
	}


	// 2. Entity -> Domain 변환
	public IssuedCoupon toDomain(IssuedCouponEntity entity) {
		return IssuedCoupon.reconstruct(
			entity.getId(),
			entity.getCouponTemplateId(),
			entity.getUserId(),
			entity.getStatus(),
			entity.getCreatedAt()
		);
	}

}
