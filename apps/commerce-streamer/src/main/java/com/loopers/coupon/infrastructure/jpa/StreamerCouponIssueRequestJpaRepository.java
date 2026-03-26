package com.loopers.coupon.infrastructure.jpa;


import com.loopers.coupon.infrastructure.entity.StreamerCouponIssueRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface StreamerCouponIssueRequestJpaRepository extends JpaRepository<StreamerCouponIssueRequestEntity, Long> {

	Optional<StreamerCouponIssueRequestEntity> findByRequestId(String requestId);

}
