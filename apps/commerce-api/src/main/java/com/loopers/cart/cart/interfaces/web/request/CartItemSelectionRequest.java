package com.loopers.cart.cart.interfaces.web.request;


import jakarta.validation.constraints.NotNull;

import java.util.List;


public record CartItemSelectionRequest(
	@NotNull List<Long> selectedCartItemIds
) {
}
