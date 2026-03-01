package com.loopers.cart.cart.infrastructure.mapper;


import com.loopers.cart.cart.domain.model.CartItem;
import com.loopers.cart.cart.domain.model.vo.Quantity;
import com.loopers.cart.cart.infrastructure.entity.CartItemEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@DisplayName("CartItemEntityMapper 테스트")
class CartItemEntityMapperTest {

	private CartItemEntityMapper mapper;


	@BeforeEach
	void setUp() {
		mapper = new CartItemEntityMapper();
	}


	@Test
	@DisplayName("[toEntity()] CartItem -> CartItemEntity 변환. 필드 매핑 확인")
	void toEntity() {
		// Arrange
		CartItem cartItem = CartItem.create(10L, 100L, 3L);

		// Act
		CartItemEntity entity = mapper.toEntity(cartItem);

		// Assert
		assertAll(
			() -> assertThat(entity.getUserId()).isEqualTo(10L),
			() -> assertThat(entity.getProductId()).isEqualTo(100L),
			() -> assertThat(entity.getQuantity()).isEqualTo(3L),
			() -> assertThat(entity.isSelected()).isTrue()
		);
	}


	@Test
	@DisplayName("[toEntity()] 선택 해제 상태인 CartItem -> selected=false인 CartItemEntity 변환")
	void toEntityDeselected() {
		// Arrange
		CartItem cartItem = CartItem.reconstruct(
			null, 10L, 100L, Quantity.from(5L), false,
			LocalDateTime.now(), LocalDateTime.now()
		);

		// Act
		CartItemEntity entity = mapper.toEntity(cartItem);

		// Assert
		assertAll(
			() -> assertThat(entity.getQuantity()).isEqualTo(5L),
			() -> assertThat(entity.isSelected()).isFalse()
		);
	}

}
