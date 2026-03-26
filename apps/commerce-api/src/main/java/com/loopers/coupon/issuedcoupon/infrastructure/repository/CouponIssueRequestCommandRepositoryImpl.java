package com.loopers.coupon.issuedcoupon.infrastructure.repository;


import com.loopers.coupon.issuedcoupon.domain.model.CouponIssueRequest;
import com.loopers.coupon.issuedcoupon.domain.repository.CouponIssueRequestCommandRepository;
import com.loopers.coupon.issuedcoupon.infrastructure.entity.CouponIssueRequestEntity;
import com.loopers.coupon.issuedcoupon.infrastructure.jpa.CouponIssueRequestJpaRepository;
import com.loopers.coupon.issuedcoupon.infrastructure.mapper.CouponIssueRequestEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;


/**
 * 쿠폰 발급 요청 명령 레포지토리 구현체
 * 1. 저장
 */

@Repository
@RequiredArgsConstructor
public class CouponIssueRequestCommandRepositoryImpl implements CouponIssueRequestCommandRepository {

	// jpa
	private final CouponIssueRequestJpaRepository jpaRepository;
	// mapper
	private final CouponIssueRequestEntityMapper mapper;


	// 1. 저장
	@Override
	public CouponIssueRequest save(CouponIssueRequest request) {
		CouponIssueRequestEntity entity = mapper.toEntity(request);
		CouponIssueRequestEntity saved = jpaRepository.save(entity);
		return mapper.toDomain(saved);
	}

}
