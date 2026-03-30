package com.loopers.coupon.infrastructure;


import com.loopers.coupon.application.port.out.IssuedCouponPort;
import com.loopers.coupon.infrastructure.entity.StreamerIssuedCouponEntity;
import com.loopers.coupon.infrastructure.jpa.StreamerIssuedCouponJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;


/**
 * 발급된 쿠폰 관리 포트 구현체
 * - StreamerIssuedCouponJpaRepository 위임
 */

@Repository
@RequiredArgsConstructor
public class IssuedCouponPortImpl implements IssuedCouponPort {

	// jpa
	private final StreamerIssuedCouponJpaRepository issuedCouponJpaRepository;


	// 1. 중복 발급 검사
	@Override
	public boolean existsByUserIdAndTemplateId(Long userId, Long couponTemplateId) {
		return issuedCouponJpaRepository.existsByUserIdAndCouponTemplateId(userId, couponTemplateId);
	}

	// 2. 템플릿별 발급 수량 조회
	@Override
	public long countByTemplateId(Long couponTemplateId) {
		return issuedCouponJpaRepository.countByCouponTemplateId(couponTemplateId);
	}

	// 3. 쿠폰 발급 (신규 INSERT)
	@Override
	public void issue(Long userId, Long couponTemplateId) {
		issuedCouponJpaRepository.save(StreamerIssuedCouponEntity.of(userId, couponTemplateId));
	}

}
