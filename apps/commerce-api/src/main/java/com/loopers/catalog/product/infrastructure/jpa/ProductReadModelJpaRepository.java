package com.loopers.catalog.product.infrastructure.jpa;


import com.loopers.catalog.product.infrastructure.entity.ProductReadModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;


public interface ProductReadModelJpaRepository extends JpaRepository<ProductReadModelEntity, Long> {

	/**
	 * 상품 Read Model JPA 리포지토리
	 * 1. 브랜드명 일괄 업데이트
	 * 2. 좋아요 수 원자적 증가
	 * 3. 좋아요 수 원자적 감소
	 * 4. 재고 업데이트
	 * 5. soft delete (deletedAt 설정)
	 * 6. 브랜드 ID로 활성 상품 ID 목록 조회
	 */

	// 1. 브랜드명 일괄 업데이트
	@Modifying
	@Query("UPDATE ProductReadModelEntity e SET e.brandName = :brandName WHERE e.brandId = :brandId")
	void updateBrandNameByBrandId(@Param("brandId") Long brandId, @Param("brandName") String brandName);

	// 1-1. 기존 Read Model 스냅샷 갱신 (createdAt/likeCount 보존)
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		UPDATE ProductReadModelEntity e
		SET e.brandId = :brandId,
		    e.brandName = :brandName,
		    e.name = :name,
		    e.price = :price,
		    e.stock = :stock,
		    e.description = :description,
		    e.updatedAt = :updatedAt,
		    e.deletedAt = :deletedAt
		WHERE e.id = :id
		""")
	int updateSnapshot(
		@Param("id") Long id,
		@Param("brandId") Long brandId,
		@Param("brandName") String brandName,
		@Param("name") String name,
		@Param("price") BigDecimal price,
		@Param("stock") Long stock,
		@Param("description") String description,
		@Param("updatedAt") ZonedDateTime updatedAt,
		@Param("deletedAt") ZonedDateTime deletedAt
	);

	// 2. 좋아요 수 원자적 증가 (영향 행 수 반환 — 대상 미존재 검증용)
	@Modifying
	@Query("UPDATE ProductReadModelEntity e SET e.likeCount = e.likeCount + 1 WHERE e.id = :id")
	int increaseLikeCount(@Param("id") Long id);

	// 3. 좋아요 수 원자적 감소 (0 이하로 내려가지 않음, 영향 행 수 반환)
	@Modifying
	@Query("UPDATE ProductReadModelEntity e SET e.likeCount = e.likeCount - 1 WHERE e.id = :id AND e.likeCount > 0")
	int decreaseLikeCount(@Param("id") Long id);

	// 4. 재고 업데이트
	@Modifying
	@Query("UPDATE ProductReadModelEntity e SET e.stock = :stock WHERE e.id = :id")
	void updateStock(@Param("id") Long id, @Param("stock") Long stock);

	// 5. soft delete (deletedAt 설정)
	@Modifying
	@Query("UPDATE ProductReadModelEntity e SET e.deletedAt = :deletedAt WHERE e.id = :productId")
	void softDelete(@Param("productId") Long productId, @Param("deletedAt") ZonedDateTime deletedAt);

	// 6. 브랜드 ID로 활성 상품 ID 목록 조회 (브랜드명 write-through용)
	@Query("SELECT e.id FROM ProductReadModelEntity e WHERE e.brandId = :brandId AND e.deletedAt IS NULL")
	List<Long> findActiveIdsByBrandId(@Param("brandId") Long brandId);

}
