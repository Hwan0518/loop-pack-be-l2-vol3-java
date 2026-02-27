package com.loopers.catalog.product.infrastructure.mapper;


import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.model.vo.Money;
import com.loopers.catalog.product.domain.model.vo.ProductDescription;
import com.loopers.catalog.product.domain.model.vo.ProductName;
import com.loopers.catalog.product.domain.model.vo.Stock;
import com.loopers.catalog.product.infrastructure.entity.ProductEntity;
import org.springframework.stereotype.Component;


/**
 * 상품 엔티티 매퍼
 * 1. Domain -> Entity 변환
 * 2. Entity -> Domain 변환
 */
@Component
public class ProductEntityMapper {

	// 1. Domain -> Entity 변환
	public ProductEntity toEntity(Product product) {

		// 엔티티 생성
		ProductEntity entity = ProductEntity.of(
			product.getId(),
			product.getBrandId(),
			product.getName().value(),
			product.getPrice().value(),
			product.getStock().value(),
			product.getDescription() != null ? product.getDescription().value() : null,
			product.getLikeCount()
		);

		// 소프트 삭제 상태 반영
		if (product.getDeletedAt() != null) {
			entity.delete();
		}

		return entity;
	}


	// 2. Entity -> Domain 변환
	public Product toDomain(ProductEntity entity) {
		return Product.reconstruct(
			entity.getId(),
			entity.getBrandId(),
			ProductName.from(entity.getName()),
			Money.from(entity.getPrice()),
			Stock.from(entity.getStock()),
			ProductDescription.from(entity.getDescription()),
			entity.getLikeCount(),
			entity.getDeletedAt()
		);
	}

}
