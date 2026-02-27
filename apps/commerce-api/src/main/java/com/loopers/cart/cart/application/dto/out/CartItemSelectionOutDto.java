package com.loopers.cart.cart.application.dto.out;


import java.util.List;


public record CartItemSelectionOutDto(
	List<Long> selectedIds,
	List<Long> deselectedIds
) {
}
