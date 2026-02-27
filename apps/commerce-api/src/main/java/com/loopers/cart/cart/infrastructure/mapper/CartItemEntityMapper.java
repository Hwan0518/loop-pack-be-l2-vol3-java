package com.loopers.cart.cart.infrastructure.mapper;


import com.loopers.cart.cart.domain.model.CartItem;
import com.loopers.cart.cart.domain.model.vo.Quantity;
import com.loopers.cart.cart.infrastructure.entity.CartItemEntity;
import org.springframework.stereotype.Component;


/**
 * 장바구니 항목 엔티티 매퍼
 * 1. Domain -> Entity 변환 (신규 생성용)
 * 2. Entity -> Domain 변환
 */
@Component
public class CartItemEntityMapper {

	// 1. Domain -> Entity 변환
	public CartItemEntity toEntity(CartItem cartItem) {
		return CartItemEntity.of(
			cartItem.getId(),
			cartItem.getUserId(),
			cartItem.getProductId(),
			cartItem.getQuantity().value(),
			cartItem.isSelected()
		);
	}


	// 2. Entity -> Domain 변환
	public CartItem toDomain(CartItemEntity entity) {
		return CartItem.reconstruct(
			entity.getId(),
			entity.getUserId(),
			entity.getProductId(),
			Quantity.from(entity.getQuantity()),
			entity.isSelected(),
			entity.getCreatedAt() != null ? entity.getCreatedAt().toLocalDateTime() : null,
			entity.getUpdatedAt() != null ? entity.getUpdatedAt().toLocalDateTime() : null
		);
	}

}
