package com.loopers.cart.cart.interfaces.web.response;


import com.loopers.cart.cart.application.dto.out.CartStatusOutDto;

import java.util.List;


public record CartStatusResponse(
	int totalCount,
	int selectedCount,
	List<CartStatusItemResponse> items
) {

	public static CartStatusResponse from(CartStatusOutDto outDto) {
		List<CartStatusItemResponse> itemResponses = outDto.items().stream()
			.map(CartStatusItemResponse::from)
			.toList();
		return new CartStatusResponse(outDto.totalCount(), outDto.selectedCount(), itemResponses);
	}

}
