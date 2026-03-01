package com.loopers.catalog.brand.infrastructure.mapper;


import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.model.vo.BrandDescription;
import com.loopers.catalog.brand.domain.model.vo.BrandName;
import com.loopers.catalog.brand.infrastructure.entity.BrandEntity;
import org.springframework.stereotype.Component;


/**
 * 브랜드 엔티티 매퍼
 * 1. Domain -> Entity 변환
 * 2. Entity -> Domain 변환
 */
@Component
public class BrandEntityMapper {

	// 1. Domain -> Entity 변환
	public BrandEntity toEntity(Brand brand) {

		// 엔티티 생성
		BrandEntity entity = BrandEntity.of(
			brand.getId(),
			brand.getName().value(),
			brand.getDescription() != null ? brand.getDescription().value() : null,
			brand.getVisibleStatus()
		);

		// 소프트 삭제 상태 반영
		if (brand.getDeletedAt() != null) {
			entity.delete();
		}

		return entity;
	}


	// 2. Entity -> Domain 변환
	public Brand toDomain(BrandEntity entity) {
		return Brand.reconstruct(
			entity.getId(),
			BrandName.from(entity.getName()),
			BrandDescription.from(entity.getDescription()),
			entity.getVisibleStatus(),
			entity.getDeletedAt()
		);
	}

}
