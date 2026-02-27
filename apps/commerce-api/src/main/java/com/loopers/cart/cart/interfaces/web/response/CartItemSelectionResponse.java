package com.loopers.cart.cart.interfaces.web.response;


import com.loopers.cart.cart.application.dto.out.CartItemSelectionOutDto;

import java.util.List;


public record CartItemSelectionResponse(
	List<Long> selectedIds,
	List<Long> deselectedIds
) {

	public static CartItemSelectionResponse from(CartItemSelectionOutDto outDto) {
		return new CartItemSelectionResponse(outDto.selectedIds(), outDto.deselectedIds());
	}

}
