package com.loopers.catalog.product.infrastructure.entity;


import com.loopers.domain.SoftDeleteBaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


/**
 * 상품 엔티티
 * - brandId: 브랜드 ID
 * - name: 상품명
 * - price: 가격
 * - stock: 재고
 * - description: 상품 설명
 * - likeCount: 좋아요 수
 * - version: 낙관적 락 버전 (@Version — JPA가 UPDATE 시 WHERE version = ? 자동 추가)
 */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductEntity extends SoftDeleteBaseEntity {

	@Column(name = "brand_id", nullable = false)
	private Long brandId;

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

	@Version
	@Column(name = "version")
	private Long version;


	private ProductEntity(Long id, Long brandId, String name, BigDecimal price, Long stock,
		String description, Long likeCount, Long version) {
		super(id);
		this.brandId = brandId;
		this.name = name;
		this.price = price;
		this.stock = stock;
		this.description = description;
		this.likeCount = likeCount;
		this.version = version;
	}


	// DB 복원용 (id + version 포함)
	public static ProductEntity of(Long id, Long brandId, String name, BigDecimal price, Long stock,
		String description, Long likeCount, Long version) {
		return new ProductEntity(id, brandId, name, price, stock, description, likeCount, version);
	}

	// 신규 생성용 (id = null, version = null — JPA가 persist 시 0으로 초기화)
	public static ProductEntity of(Long brandId, String name, BigDecimal price, Long stock,
		String description, Long likeCount) {
		return of(null, brandId, name, price, stock, description, likeCount, null);
	}

}
