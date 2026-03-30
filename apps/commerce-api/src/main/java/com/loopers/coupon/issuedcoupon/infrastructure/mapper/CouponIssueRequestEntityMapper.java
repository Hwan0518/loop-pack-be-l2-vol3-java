package com.loopers.coupon.issuedcoupon.infrastructure.mapper;


import com.loopers.coupon.issuedcoupon.domain.model.CouponIssueRequest;
import com.loopers.coupon.issuedcoupon.domain.model.enums.CouponIssueRequestStatus;
import com.loopers.coupon.issuedcoupon.infrastructure.entity.CouponIssueRequestEntity;
import org.springframework.stereotype.Component;


/**
 * 쿠폰 발급 요청 엔티티 매퍼
 * 1. Domain -> Entity 변환
 * 2. Entity -> Domain 변환
 */
@Component
public class CouponIssueRequestEntityMapper {

	// 1. Domain -> Entity 변환
	public CouponIssueRequestEntity toEntity(CouponIssueRequest request) {
		return CouponIssueRequestEntity.of(
			request.getRequestId(),
			request.getUserId(),
			request.getCouponTemplateId(),
			request.getStatus().name()
		);
	}

	// 2. Entity -> Domain 변환
	public CouponIssueRequest toDomain(CouponIssueRequestEntity entity) {
		return CouponIssueRequest.reconstruct(
			entity.getId(),
			entity.getRequestId(),
			entity.getUserId(),
			entity.getCouponTemplateId(),
			CouponIssueRequestStatus.valueOf(entity.getStatus()),
			entity.getRejectReason(),
			entity.getCreatedAt()
		);
	}

}
