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


	private ProductEntity(Long id, Long brandId, String name, BigDecimal price, Long stock,
		String description) {
		super(id);
		this.brandId = brandId;
		this.name = name;
		this.price = price;
		this.stock = stock;
		this.description = description;
	}


	// DB 복원용 (id 포함)
	public static ProductEntity of(Long id, Long brandId, String name, BigDecimal price, Long stock,
		String description) {
		return new ProductEntity(id, brandId, name, price, stock, description);
	}

	// 신규 생성용 (id = null)
	public static ProductEntity of(Long brandId, String name, BigDecimal price, Long stock,
		String description) {
		return of(null, brandId, name, price, stock, description);
	}

}
