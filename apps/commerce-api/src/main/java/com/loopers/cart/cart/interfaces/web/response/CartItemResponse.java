package com.loopers.cart.cart.interfaces.web.response;


import com.loopers.cart.cart.application.dto.out.CartItemOutDto;

import java.time.LocalDateTime;


public record CartItemResponse(
	Long id,
	Long productId,
	Long quantity,
	boolean selected,
	LocalDateTime createdAt
) {

	public static CartItemResponse from(CartItemOutDto outDto) {
		return new CartItemResponse(
			outDto.id(),
			outDto.productId(),
			outDto.quantity(),
			outDto.selected(),
			outDto.createdAt()
		);
	}

}
