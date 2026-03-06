package com.loopers.coupon.coupontemplate.infrastructure.repository;


import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.coupontemplate.domain.repository.CouponTemplateCommandRepository;
import com.loopers.coupon.coupontemplate.infrastructure.entity.CouponTemplateEntity;
import com.loopers.coupon.coupontemplate.infrastructure.jpa.CouponTemplateJpaRepository;
import com.loopers.coupon.coupontemplate.infrastructure.mapper.CouponTemplateEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;


@Repository
@RequiredArgsConstructor
public class CouponTemplateCommandRepositoryImpl implements CouponTemplateCommandRepository {

	// jpa
	private final CouponTemplateJpaRepository couponTemplateJpaRepository;
	// mapper
	private final CouponTemplateEntityMapper couponTemplateEntityMapper;


	/**
	 * 쿠폰 템플릿 명령 리포지토리 구현체
	 * 1. 쿠폰 템플릿 저장
	 */

	// 1. 쿠폰 템플릿 저장
	@Override
	public CouponTemplate save(CouponTemplate couponTemplate) {

		// 엔티티로 변환
		CouponTemplateEntity entity = couponTemplateEntityMapper.toEntity(couponTemplate);

		// 저장
		CouponTemplateEntity savedEntity = couponTemplateJpaRepository.save(entity);

		// 결과 반환
		return couponTemplateEntityMapper.toDomain(savedEntity);
	}

}
