package com.loopers.catalog.product.infrastructure.repository;


import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.repository.ProductReadModelRepository;
import com.loopers.catalog.product.infrastructure.entity.ProductReadModelEntity;
import com.loopers.catalog.product.infrastructure.jpa.ProductReadModelJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;


@Repository
@RequiredArgsConstructor
public class ProductReadModelRepositoryImpl implements ProductReadModelRepository {

	// jpa
	private final ProductReadModelJpaRepository jpaRepository;


	@Override
	public void save(Product product, String brandName) {
		ZonedDateTime updatedAt = ZonedDateTime.now();
		int updatedRows = jpaRepository.updateSnapshot(
			product.getId(),
			product.getBrandId(),
			brandName,
			product.getName().value(),
			product.getPrice().value(),
			product.getStock().value(),
			product.getDescription() != null ? product.getDescription().value() : null,
			updatedAt,
			product.getDeletedAt()
		);

		if (updatedRows > 0) {
			return;
		}

		jpaRepository.save(ProductReadModelEntity.of(product, brandName));
	}


	@Override
	public void softDelete(Long productId) {
		// deletedAt을 현재 시각으로 설정하여 soft delete 처리
		jpaRepository.softDelete(productId, ZonedDateTime.now());
	}


	@Override
	public void updateStock(Long productId, Long newStock) {
		jpaRepository.updateStock(productId, newStock);
	}


	@Override
	public void updateBrandName(Long brandId, String newBrandName) {
		jpaRepository.updateBrandNameByBrandId(brandId, newBrandName);
	}


	@Override
	public List<Long> findActiveIdsByBrandId(Long brandId) {
		return jpaRepository.findActiveIdsByBrandId(brandId);
	}

}
