package com.loopers.cart.cart.interfaces.web.response;


import com.loopers.cart.cart.application.dto.out.CartOutDto;

import java.util.List;


public record CartResponse(
	List<CartItemResponse> items,
	int totalCount
) {

	public static CartResponse from(CartOutDto outDto) {
		List<CartItemResponse> itemResponses = outDto.items().stream()
			.map(CartItemResponse::from)
			.toList();
		return new CartResponse(itemResponses, outDto.totalCount());
	}

}
