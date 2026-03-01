package com.loopers.cart.cart.interfaces.web.request;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;


public record CartItemAddRequest(
	@NotNull Long productId,
	@NotNull @Min(1) Long quantity
) {
}
