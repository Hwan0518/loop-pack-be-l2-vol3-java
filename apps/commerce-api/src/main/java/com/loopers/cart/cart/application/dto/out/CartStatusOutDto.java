package com.loopers.cart.cart.application.dto.out;


import java.util.List;


public record CartStatusOutDto(
	int totalCount,
	int selectedCount,
	List<CartStatusItemOutDto> items
) {
}
