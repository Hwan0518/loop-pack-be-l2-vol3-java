package com.loopers.coupon.issuedcoupon.infrastructure.repository;


import com.loopers.coupon.issuedcoupon.domain.model.IssuedCoupon;
import com.loopers.coupon.issuedcoupon.domain.repository.IssuedCouponCommandRepository;
import com.loopers.coupon.issuedcoupon.infrastructure.entity.IssuedCouponEntity;
import com.loopers.coupon.issuedcoupon.infrastructure.jpa.IssuedCouponJpaRepository;
import com.loopers.coupon.issuedcoupon.infrastructure.mapper.IssuedCouponEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;


@Repository
@RequiredArgsConstructor
public class IssuedCouponCommandRepositoryImpl implements IssuedCouponCommandRepository {

	// jpa
	private final IssuedCouponJpaRepository issuedCouponJpaRepository;
	// mapper
	private final IssuedCouponEntityMapper issuedCouponEntityMapper;


	/**
	 * 발급 쿠폰 명령 리포지토리 구현체
	 * 1. 발급 쿠폰 저장
	 */

	// 1. 발급 쿠폰 저장
	@Override
	public IssuedCoupon save(IssuedCoupon issuedCoupon) {

		// 엔티티로 변환
		IssuedCouponEntity entity = issuedCouponEntityMapper.toEntity(issuedCoupon);

		// 저장
		IssuedCouponEntity savedEntity = issuedCouponJpaRepository.save(entity);

		// 결과 반환
		return issuedCouponEntityMapper.toDomain(savedEntity);
	}

}
