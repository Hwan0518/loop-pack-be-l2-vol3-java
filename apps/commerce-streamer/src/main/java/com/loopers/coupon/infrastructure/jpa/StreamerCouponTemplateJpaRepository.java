package com.loopers.coupon.infrastructure.jpa;


import com.loopers.coupon.infrastructure.entity.StreamerCouponTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;


public interface StreamerCouponTemplateJpaRepository extends JpaRepository<StreamerCouponTemplateEntity, Long> {
}
