package com.loopers.coupon.issuedcoupon.infrastructure.jpa;


import com.loopers.coupon.issuedcoupon.infrastructure.entity.CouponIssueRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


/**
 * 쿠폰 발급 요청 JPA 레포지토리
 */
public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequestEntity, Long> {

	Optional<CouponIssueRequestEntity> findByRequestId(String requestId);

	Optional<CouponIssueRequestEntity> findByRequestIdAndUserId(String requestId, Long userId);

}
