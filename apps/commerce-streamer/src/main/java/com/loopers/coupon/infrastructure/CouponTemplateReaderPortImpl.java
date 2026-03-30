package com.loopers.coupon.infrastructure;


import com.loopers.coupon.application.dto.CouponTemplateInfo;
import com.loopers.coupon.application.port.out.CouponTemplateReaderPort;
import com.loopers.coupon.infrastructure.entity.StreamerCouponTemplateEntity;
import com.loopers.coupon.infrastructure.jpa.StreamerCouponTemplateJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;


/**
 * 쿠폰 템플릿 조회 포트 구현체
 * - StreamerCouponTemplateJpaRepository 위임
 */

@Repository
@RequiredArgsConstructor
public class CouponTemplateReaderPortImpl implements CouponTemplateReaderPort {

	// jpa
	private final StreamerCouponTemplateJpaRepository couponTemplateJpaRepository;


	// 1. 템플릿 조회 → CouponTemplateInfo DTO 변환
	@Override
	public Optional<CouponTemplateInfo> findById(Long couponTemplateId) {
		return couponTemplateJpaRepository.findById(couponTemplateId)
			.map(this::toInfo);
	}


	// entity → DTO 변환
	private CouponTemplateInfo toInfo(StreamerCouponTemplateEntity entity) {
		return new CouponTemplateInfo(
			entity.getId(),
			entity.getMaxQuantity(),
			entity.isDeleted(),
			entity.isExpired()
		);
	}

}
