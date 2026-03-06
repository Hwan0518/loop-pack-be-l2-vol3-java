package com.loopers.catalog.product.domain.model;


import com.loopers.catalog.product.domain.model.vo.Money;
import com.loopers.catalog.product.domain.model.vo.ProductDescription;
import com.loopers.catalog.product.domain.model.vo.ProductName;
import com.loopers.catalog.product.domain.model.vo.Stock;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Objects;


@Getter
public class Product {

	/**
	 * 상품
	 * - id: 상품 ID
	 * - brandId: 브랜드 ID
	 * - name: 상품명
	 * - price: 가격
	 * - stock: 재고
	 * - description: 상품 설명
	 * - likeCount: 좋아요 수
	 * - deletedAt: 삭제 일시 (soft delete)
	 */

	private final Long id;
	private final Long brandId;
	private ProductName name;
	private Money price;
	private Stock stock;
	private ProductDescription description;
	private Long likeCount;
	private ZonedDateTime deletedAt;


	// 생성자
	private Product(Long id, Long brandId, ProductName name, Money price, Stock stock,
		ProductDescription description, Long likeCount, ZonedDateTime deletedAt) {
		this.id = id;
		this.brandId = brandId;
		this.name = name;
		this.price = price;
		this.stock = stock;
		this.description = description;
		this.likeCount = likeCount;
		this.deletedAt = deletedAt;
	}


	/**
	 * 도메인 로직
	 * 1. 상품 생성
	 * 2. 상품 재생성 (Entity -> Model 매핑용도)
	 * 3. 상품명 변경
	 * 4. 가격 변경
	 * 5. 재고 변경
	 * 6. 설명 변경
	 * 7. 재고 차감
	 * 8. 삭제 (soft delete)
	 * 9. 삭제 여부 확인
	 */

	// 1. 상품 생성
	public static Product create(Long brandId, String name, BigDecimal price, Long stock, String description) {

		// brandId null 검증
		Objects.requireNonNull(brandId, "brandId");

		// VO 생성 (검증 포함)
		ProductName productName = ProductName.create(name);
		Money productPrice = Money.create(price);
		Stock productStock = Stock.create(stock);
		ProductDescription productDescription = ProductDescription.create(description);

		// 상품 생성 (기본 좋아요 수: 0)
		return new Product(null, brandId, productName, productPrice, productStock, productDescription, 0L, null);
	}


	// 2. 상품 재생성 (Entity -> Model 매핑용도)
	public static Product reconstruct(Long id, Long brandId, ProductName name, Money price, Stock stock,
		ProductDescription description, Long likeCount, ZonedDateTime deletedAt) {
		return new Product(id, brandId, name, price, stock, description, likeCount, deletedAt);
	}


	// 3. 상품명 변경
	public void changeName(String rawName) {
		this.name = ProductName.create(rawName);
	}


	// 4. 가격 변경
	public void changePrice(BigDecimal rawPrice) {
		this.price = Money.create(rawPrice);
	}


	// 5. 재고 변경
	public void changeStock(Long rawStock) {
		this.stock = Stock.create(rawStock);
	}


	// 6. 설명 변경
	public void changeDescription(String rawDescription) {
		this.description = ProductDescription.create(rawDescription);
	}


	// 7. 재고 차감
	public void decreaseStock(Long quantity) {
		this.stock = this.stock.decrease(quantity);
	}


	// 8. 삭제 (soft delete)
	public void delete() {

		// 이미 삭제된 경우 예외
		if (isDeleted()) {
			throw new CoreException(ErrorType.PRODUCT_NOT_FOUND);
		}

		this.deletedAt = ZonedDateTime.now();
	}


	// 9. 삭제 여부 확인
	public boolean isDeleted() {
		return this.deletedAt != null;
	}

}
