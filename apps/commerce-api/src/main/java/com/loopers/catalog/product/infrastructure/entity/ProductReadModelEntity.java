package com.loopers.catalog.product.infrastructure.entity;


import com.loopers.catalog.product.domain.model.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;


/**
 * 상품 Read Model 엔티티
 * - id: 상품 ID (products.id와 동일, AUTO_INCREMENT 아님)
 * - brandId: 브랜드 ID
 * - brandName: 브랜드명 (비정규화)
 * - name: 상품명
 * - price: 가격
 * - stock: 재고
 * - description: 상품 설명
 * - likeCount: 좋아요 수
 * - createdAt: 생성 일시
 * - updatedAt: 수정 일시
 * - deletedAt: 삭제 일시 (soft delete)
 */
@Entity
@Table(name = "product_read_model", indexes = {
	// 사용자 조회: WHERE brand_id = ? AND deleted_at IS NULL ORDER BY {sort_col}
	// 컬럼 순서: 카디널리티 높은 brand_id 선두 → deleted_at(equality) → sort_col
	@Index(name = "idx_read_brand_deleted_created", columnList = "brand_id, deleted_at, created_at"),
	@Index(name = "idx_read_brand_deleted_price", columnList = "brand_id, deleted_at, price"),
	@Index(name = "idx_read_brand_deleted_likecount", columnList = "brand_id, deleted_at, like_count"),
	// 사용자 조회 (브랜드 미지정): WHERE deleted_at IS NULL ORDER BY {sort_col}
	@Index(name = "idx_read_deleted_created", columnList = "deleted_at, created_at"),
	@Index(name = "idx_read_deleted_price", columnList = "deleted_at, price"),
	@Index(name = "idx_read_deleted_likecount", columnList = "deleted_at, like_count"),
	// 관리자 조회 (브랜드 지정): WHERE brand_id = ? ORDER BY {sort_col}
	@Index(name = "idx_read_brand_created", columnList = "brand_id, created_at"),
	@Index(name = "idx_read_brand_price", columnList = "brand_id, price"),
	@Index(name = "idx_read_brand_likecount", columnList = "brand_id, like_count"),
	// 관리자 조회 (필터 없음): ORDER BY {sort_col}
	@Index(name = "idx_read_created", columnList = "created_at"),
	@Index(name = "idx_read_price", columnList = "price"),
	@Index(name = "idx_read_likecount", columnList = "like_count")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductReadModelEntity {

	@Id
	private Long id;

	@Column(name = "brand_id", nullable = false)
	private Long brandId;

	@Column(name = "brand_name", length = 100)
	private String brandName;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "price", nullable = false, precision = 12, scale = 2)
	private BigDecimal price;

	@Column(name = "stock", nullable = false)
	private Long stock;

	@Column(name = "description", length = 1000)
	private String description;

	@Column(name = "like_count", nullable = false)
	private Long likeCount;

	@Column(name = "created_at", nullable = false)
	private ZonedDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private ZonedDateTime updatedAt;

	@Column(name = "deleted_at")
	private ZonedDateTime deletedAt;


	private ProductReadModelEntity(Long id, Long brandId, String brandName, String name,
		BigDecimal price, Long stock, String description, Long likeCount,
		ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
		this.id = id;
		this.brandId = brandId;
		this.brandName = brandName;
		this.name = name;
		this.price = price;
		this.stock = stock;
		this.description = description;
		this.likeCount = likeCount;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.deletedAt = deletedAt;
	}


	// 도메인 모델 + 브랜드명으로 Read Model 엔티티 생성 (신규: createdAt = now, likeCount = 0)
	public static ProductReadModelEntity of(Product product, String brandName) {
		return of(product, brandName, ZonedDateTime.now(), 0L);
	}


	// 도메인 모델 + 브랜드명 + 기존 createdAt/likeCount 보존 (업데이트 시 LATEST 정렬 오염 + likeCount 덮어쓰기 방지)
	public static ProductReadModelEntity of(Product product, String brandName,
		ZonedDateTime createdAt, Long likeCount) {

		return new ProductReadModelEntity(
			product.getId(),
			product.getBrandId(),
			brandName,
			product.getName().value(),
			product.getPrice().value(),
			product.getStock().value(),
			product.getDescription() != null ? product.getDescription().value() : null,
			likeCount,
			createdAt,
			ZonedDateTime.now(),
			product.getDeletedAt()
		);
	}

}
