package com.loopers.catalog.product.infrastructure.repository;


import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.repository.ProductReadModelRepository;
import com.loopers.catalog.product.infrastructure.entity.ProductReadModelEntity;
import com.loopers.catalog.product.infrastructure.jpa.ProductReadModelJpaRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

		try {
			jpaRepository.save(ProductReadModelEntity.of(product, brandName));
		} catch (DataIntegrityViolationException e) {
			// 다른 트랜잭션이 먼저 insert한 경우 스냅샷 update로 재시도한다.
			jpaRepository.updateSnapshot(
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
		}
	}


	@Override
	public void softDelete(Long productId) {
		// deletedAt을 현재 시각으로 설정하여 soft delete 처리
		jpaRepository.softDelete(productId, ZonedDateTime.now());
	}


	@Override
	public void increaseLikeCount(Long productId) {

		// 원자적 증가 (단일 SQL UPDATE)
		int updatedRows = jpaRepository.increaseLikeCount(productId);

		// 대상 Read Model 미존재 시 예외
		if (updatedRows == 0) {
			throw new CoreException(ErrorType.PRODUCT_NOT_FOUND);
		}
	}


	@Override
	public void decreaseLikeCount(Long productId) {

		// 원자적 감소 (단일 SQL UPDATE — 0 이하로 내려가지 않음)
		int updatedRows = jpaRepository.decreaseLikeCount(productId);

		// 대상 Read Model 미존재 시 예외 (likeCount가 이미 0인 경우는 정상 — 0행 반환 허용)
		// Note: decreaseLikeCount WHERE likeCount > 0 조건으로 0행 반환은 이미 0인 경우도 포함
		// 따라서 여기서는 검증하지 않음 (음수 방지가 목적)
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
