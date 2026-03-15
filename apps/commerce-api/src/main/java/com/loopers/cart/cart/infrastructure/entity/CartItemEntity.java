package com.loopers.cart.cart.infrastructure.entity;


import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * 장바구니 항목 엔티티
 * - userId: 사용자 ID
 * - productId: 상품 ID
 * - quantity: 수량
 * - selected: 선택 여부
 */
@Entity
@Table(name = "cart_items",
	uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"}),
	indexes = {
		// 선택된 장바구니 항목 조회: WHERE user_id = ? AND selected = true
		@Index(name = "idx_cart_user_selected", columnList = "user_id, selected"),
		// 상품 삭제 시 장바구니 정리: WHERE product_id = ?
		@Index(name = "idx_cart_product", columnList = "product_id")
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItemEntity extends BaseEntity {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "quantity", nullable = false)
	private Long quantity;

	@Column(name = "selected", nullable = false)
	private boolean selected;


	private CartItemEntity(Long id, Long userId, Long productId, Long quantity, boolean selected) {
		super(id);
		this.userId = userId;
		this.productId = productId;
		this.quantity = quantity;
		this.selected = selected;
	}


	public static CartItemEntity of(Long id, Long userId, Long productId, Long quantity, boolean selected) {
		return new CartItemEntity(id, userId, productId, quantity, selected);
	}


	public static CartItemEntity of(Long userId, Long productId, Long quantity, boolean selected) {
		return of(null, userId, productId, quantity, selected);
	}

}
