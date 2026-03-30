package com.loopers.coupon.coupontemplate.infrastructure.mapper;


import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.coupontemplate.infrastructure.entity.CouponTemplateEntity;
import org.springframework.stereotype.Component;


/**
 * 쿠폰 템플릿 엔티티 매퍼
 * 1. Domain -> Entity 변환
 * 2. Entity -> Domain 변환
 */
@Component
public class CouponTemplateEntityMapper {

	// 1. Domain -> Entity 변환
	public CouponTemplateEntity toEntity(CouponTemplate template) {

		// 엔티티 생성 (id 포함 — id가 null이면 신규, 있으면 수정)
		CouponTemplateEntity entity = CouponTemplateEntity.of(
			template.getId(),
			template.getName(),
			template.getType(),
			template.getValue(),
			template.getMinOrderAmount(),
			template.getMaxQuantity(),
			template.getExpiredAt()
		);

		// 소프트 삭제 상태 반영
		if (template.isDeleted()) {
			entity.delete();
		}

		return entity;
	}


	// 2. Entity -> Domain 변환
	public CouponTemplate toDomain(CouponTemplateEntity entity) {
		return CouponTemplate.reconstruct(
			entity.getId(),
			entity.getName(),
			entity.getType(),
			entity.getValue(),
			entity.getMinOrderAmount(),
			entity.getMaxQuantity(),
			entity.getExpiredAt(),
			entity.getDeletedAt()
		);
	}

}
