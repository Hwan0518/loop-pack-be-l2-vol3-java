package com.loopers.cart.cart.application.dto.out;


import com.loopers.cart.cart.domain.model.CartItem;

import java.util.List;


public record CartOutDto(
	List<CartItemOutDto> items,
	int totalCount
) {

	public static CartOutDto from(List<CartItem> cartItems) {
		List<CartItemOutDto> itemDtos = cartItems.stream()
			.map(CartItemOutDto::from)
			.toList();
		return new CartOutDto(itemDtos, itemDtos.size());
	}

}
