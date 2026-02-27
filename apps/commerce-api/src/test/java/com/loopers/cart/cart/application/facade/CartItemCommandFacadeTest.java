package com.loopers.cart.cart.application.facade;


import com.loopers.cart.cart.application.dto.in.CartItemAddInDto;
import com.loopers.cart.cart.application.dto.in.CartItemSelectionInDto;
import com.loopers.cart.cart.application.dto.in.CartItemUpdateQuantityInDto;
import com.loopers.cart.cart.application.dto.out.CartItemOutDto;
import com.loopers.cart.cart.application.dto.out.CartItemSelectionOutDto;
import com.loopers.cart.cart.application.service.CartItemCommandService;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("CartItemCommandFacade 단위 테스트")
class CartItemCommandFacadeTest {

	@Mock
	private CartItemCommandService cartItemCommandService;

	private CartItemCommandFacade cartItemCommandFacade;

	private static final String LOGIN_ID = "testuser";
	private static final String PASSWORD = "password123";
	private static final Long USER_ID = 1L;


	@BeforeEach
	void setUp() {
		cartItemCommandFacade = new CartItemCommandFacade(cartItemCommandService);
	}


	@Nested
	@DisplayName("addItem()")
	class AddItemTest {

		@Test
		@DisplayName("[addItem()] 유효한 상품 -> 서비스에서 상품 검증 후 항목 추가, CartItemOutDto 반환")
		void addItemSuccess() {
			// Arrange
			CartItemAddInDto inDto = new CartItemAddInDto(100L, 3L);
			CartItem savedItem = CartItem.reconstruct(1L, USER_ID, 100L, Quantity.from(3L), true,
				LocalDateTime.now(), LocalDateTime.now());

			given(cartItemCommandService.authenticate(LOGIN_ID, PASSWORD)).willReturn(USER_ID);
			given(cartItemCommandService.addItem(eq(USER_ID), any(CartItemAddInDto.class))).willReturn(savedItem);

			// Act
			CartItemOutDto result = cartItemCommandFacade.addItem(LOGIN_ID, PASSWORD, inDto);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.productId()).isEqualTo(100L),
				() -> assertThat(result.quantity()).isEqualTo(3L)
			);
		}

	}


	@Nested
	@DisplayName("updateQuantity()")
	class UpdateQuantityTest {

		@Test
		@DisplayName("[updateQuantity()] 유효한 요청 -> 수량 변경, CartItemOutDto 반환")
		void updateQuantitySuccess() {
			// Arrange
			Long cartItemId = 1L;
			CartItemUpdateQuantityInDto inDto = new CartItemUpdateQuantityInDto(10L);
			CartItem updatedItem = CartItem.reconstruct(cartItemId, USER_ID, 100L, Quantity.from(10L), true,
				LocalDateTime.now(), LocalDateTime.now());

			given(cartItemCommandService.authenticate(LOGIN_ID, PASSWORD)).willReturn(USER_ID);
			given(cartItemCommandService.updateQuantity(eq(cartItemId), eq(USER_ID), any(CartItemUpdateQuantityInDto.class)))
				.willReturn(updatedItem);

			// Act
			CartItemOutDto result = cartItemCommandFacade.updateQuantity(LOGIN_ID, PASSWORD, cartItemId, inDto);

			// Assert
			assertThat(result.quantity()).isEqualTo(10L);
		}

	}


	@Nested
	@DisplayName("deleteItem()")
	class DeleteItemTest {

		@Test
		@DisplayName("[deleteItem()] 유효한 요청 -> 서비스 deleteItem 호출")
		void deleteItemSuccess() {
			// Arrange
			given(cartItemCommandService.authenticate(LOGIN_ID, PASSWORD)).willReturn(USER_ID);
			willDoNothing().given(cartItemCommandService).deleteItem(1L, USER_ID);

			// Act
			cartItemCommandFacade.deleteItem(LOGIN_ID, PASSWORD, 1L);

			// Assert
			verify(cartItemCommandService).deleteItem(1L, USER_ID);
		}

	}


	@Nested
	@DisplayName("updateSelection()")
	class UpdateSelectionTest {

		@Test
		@DisplayName("[updateSelection()] 유효한 요청 -> CartItemSelectionOutDto 반환")
		void updateSelectionSuccess() {
			// Arrange
			CartItemSelectionInDto inDto = new CartItemSelectionInDto(List.of(1L, 3L));
			CartItemSelectionOutDto outDto = new CartItemSelectionOutDto(List.of(1L, 3L), List.of(2L));

			given(cartItemCommandService.authenticate(LOGIN_ID, PASSWORD)).willReturn(USER_ID);
			given(cartItemCommandService.updateSelection(eq(USER_ID), any(CartItemSelectionInDto.class)))
				.willReturn(outDto);

			// Act
			CartItemSelectionOutDto result = cartItemCommandFacade.updateSelection(LOGIN_ID, PASSWORD, inDto);

			// Assert
			assertAll(
				() -> assertThat(result.selectedIds()).containsExactlyInAnyOrder(1L, 3L),
				() -> assertThat(result.deselectedIds()).containsExactly(2L)
			);
		}

	}

}
