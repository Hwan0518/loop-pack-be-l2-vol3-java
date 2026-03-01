package com.loopers.cart.cart.interfaces.web.response;


import com.loopers.cart.cart.application.dto.out.CartStatusItemOutDto;


public record CartStatusItemResponse(
	Long cartItemId,
	Long productId,
	Long quantity,
	boolean selected
) {

	public static CartStatusItemResponse from(CartStatusItemOutDto outDto) {
		return new CartStatusItemResponse(
			outDto.cartItemId(),
			outDto.productId(),
			outDto.quantity(),
			outDto.selected()
		);
	}

}
