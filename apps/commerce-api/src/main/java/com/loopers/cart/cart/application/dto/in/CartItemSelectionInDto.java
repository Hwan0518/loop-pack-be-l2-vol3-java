package com.loopers.cart.cart.application.dto.in;


import java.util.List;


public record CartItemSelectionInDto(List<Long> selectedCartItemIds) {
}
