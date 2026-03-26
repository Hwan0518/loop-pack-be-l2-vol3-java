package com.loopers.coupon.infrastructure.jpa;


import com.loopers.coupon.infrastructure.entity.StreamerIssuedCouponEntity;
import org.springframework.data.jpa.repository.JpaRepository;


public interface StreamerIssuedCouponJpaRepository extends JpaRepository<StreamerIssuedCouponEntity, Long> {

	boolean existsByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId);

	long countByCouponTemplateId(Long couponTemplateId);

}
