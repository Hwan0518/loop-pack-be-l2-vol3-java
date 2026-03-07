package com.loopers.cart.cart.application.facade;


import com.loopers.cart.cart.application.dto.out.CartOutDto;
import com.loopers.cart.cart.application.dto.out.CartStatusOutDto;
import com.loopers.cart.cart.application.dto.out.CartStatusItemOutDto;
import com.loopers.cart.cart.application.service.CartItemQueryService;
import com.loopers.cart.cart.domain.model.CartItem;
import com.loopers.cart.cart.domain.model.vo.Quantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
@DisplayName("CartItemQueryFacade 단위 테스트")
class CartItemQueryFacadeTest {

	@Mock
	private CartItemQueryService cartItemQueryService;

	private CartItemQueryFacade cartItemQueryFacade;

	private static final Long USER_ID = 1L;


	@BeforeEach
	void setUp() {
		cartItemQueryFacade = new CartItemQueryFacade(cartItemQueryService);
	}


	@Nested
	@DisplayName("getCart()")
	class GetCartTest {

		@Test
		@DisplayName("[getCart()] 장바구니 항목 존재 -> CartOutDto 반환. items, totalCount 포함")
		void getCartSuccess() {
			// Arrange
			CartItem item1 = CartItem.reconstruct(1L, USER_ID, 100L, Quantity.from(1L), true,
				LocalDateTime.now(), LocalDateTime.now());
			CartItem item2 = CartItem.reconstruct(2L, USER_ID, 200L, Quantity.from(2L), false,
				LocalDateTime.now(), LocalDateTime.now());

			given(cartItemQueryService.getCartByUserId(USER_ID)).willReturn(List.of(item1, item2));

			// Act
			CartOutDto result = cartItemQueryFacade.getCart(USER_ID);

			// Assert
			assertAll(
				() -> assertThat(result.items()).hasSize(2),
				() -> assertThat(result.totalCount()).isEqualTo(2)
			);
		}


		@Test
		@DisplayName("[getCart()] 장바구니 비어있음 -> 빈 CartOutDto 반환. totalCount=0")
		void getCartEmpty() {
			// Arrange
			Long emptyUserId = 999L;
			given(cartItemQueryService.getCartByUserId(emptyUserId)).willReturn(List.of());

			// Act
			CartOutDto result = cartItemQueryFacade.getCart(emptyUserId);

			// Assert
			assertAll(
				() -> assertThat(result.items()).isEmpty(),
				() -> assertThat(result.totalCount()).isEqualTo(0)
			);
		}

	}


	@Nested
	@DisplayName("getCartStatus()")
	class GetCartStatusTest {

		@Test
		@DisplayName("[getCartStatus()] 장바구니 상태 조회 -> CartStatusOutDto 반환")
		void getCartStatusSuccess() {
			// Arrange
			CartStatusOutDto statusOutDto = new CartStatusOutDto(3, 2,
				List.of(
					new CartStatusItemOutDto(1L, 100L, 1L, true),
					new CartStatusItemOutDto(2L, 200L, 2L, true),
					new CartStatusItemOutDto(3L, 300L, 3L, false)
				));

			given(cartItemQueryService.getCartStatus(USER_ID)).willReturn(statusOutDto);

			// Act
			CartStatusOutDto result = cartItemQueryFacade.getCartStatus(USER_ID);

			// Assert
			assertAll(
				() -> assertThat(result.totalCount()).isEqualTo(3),
				() -> assertThat(result.selectedCount()).isEqualTo(2),
				() -> assertThat(result.items()).hasSize(3)
			);
		}

	}

}
