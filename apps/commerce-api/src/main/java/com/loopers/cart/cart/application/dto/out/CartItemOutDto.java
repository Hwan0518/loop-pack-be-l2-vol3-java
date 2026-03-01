package com.loopers.cart.cart.application.dto.out;


import com.loopers.cart.cart.domain.model.CartItem;

import java.time.LocalDateTime;


public record CartItemOutDto(
	Long id,
	Long userId,
	Long productId,
	Long quantity,
	boolean selected,
	LocalDateTime createdAt
) {

	public static CartItemOutDto from(CartItem cartItem) {
		return new CartItemOutDto(
			cartItem.getId(),
			cartItem.getUserId(),
			cartItem.getProductId(),
			cartItem.getQuantity().value(),
			cartItem.isSelected(),
			cartItem.getCreatedAt()
		);
	}

}
