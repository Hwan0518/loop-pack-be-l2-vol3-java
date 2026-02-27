package com.loopers.cart.cart.application.dto.out;


import com.loopers.cart.cart.domain.model.CartItem;


public record CartStatusItemOutDto(
	Long cartItemId,
	Long productId,
	Long quantity,
	boolean selected
) {

	public static CartStatusItemOutDto from(CartItem cartItem) {
		return new CartStatusItemOutDto(
			cartItem.getId(),
			cartItem.getProductId(),
			cartItem.getQuantity().value(),
			cartItem.isSelected()
		);
	}

}
