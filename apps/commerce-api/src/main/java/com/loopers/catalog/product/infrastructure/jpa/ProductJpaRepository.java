package com.loopers.catalog.product.infrastructure.jpa;


import com.loopers.catalog.product.infrastructure.entity.ProductEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

	/**
	 * 상품 JPA 리포지토리
	 * 1. ID로 활성 상품 엔티티 조회
	 * 2. 브랜드의 활성 상품 존재 여부 확인
	 * 3. ID로 활성 상품 엔티티 조회 (비관적 쓰기 락)
	 * 4. ID 목록으로 활성 상품 엔티티 일괄 조회
	 * 5. 좋아요 수 원자적 증가
	 * 6. 좋아요 수 원자적 감소
	 */

	// 1. ID로 활성 상품 엔티티 조회
	Optional<ProductEntity> findByIdAndDeletedAtIsNull(Long id);

	// 2. 브랜드의 활성 상품 존재 여부 확인
	boolean existsByBrandIdAndDeletedAtIsNull(Long brandId);

	// 3. ID로 활성 상품 엔티티 조회 (비관적 쓰기 락 — SELECT ... FOR UPDATE)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM ProductEntity p WHERE p.id = :id AND p.deletedAt IS NULL")
	Optional<ProductEntity> findByIdAndDeletedAtIsNullForUpdate(@Param("id") Long id);

	// 4. ID 목록으로 활성 상품 엔티티 일괄 조회
	List<ProductEntity> findByIdInAndDeletedAtIsNull(List<Long> ids);

	// 5. 좋아요 수 원자적 증가 (단일 SQL — version도 함께 증가)
	@Modifying
	@Query(value = "UPDATE products SET like_count = like_count + 1, version = version + 1 WHERE id = :id AND deleted_at IS NULL", nativeQuery = true)
	int increaseLikeCount(@Param("id") Long id);

	// 6. 좋아요 수 원자적 감소 (0 이하로 내려가지 않음, version도 함께 증가)
	@Modifying
	@Query(value = "UPDATE products SET like_count = CASE WHEN like_count > 0 THEN like_count - 1 ELSE 0 END, version = version + 1 WHERE id = :id AND deleted_at IS NULL", nativeQuery = true)
	int decreaseLikeCount(@Param("id") Long id);

}
