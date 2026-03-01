package com.loopers.cart.cart.application.service;


import com.loopers.cart.cart.application.dto.out.CartStatusOutDto;
import com.loopers.cart.cart.domain.model.CartItem;
import com.loopers.cart.cart.domain.model.vo.Quantity;
import com.loopers.cart.cart.domain.repository.CartItemQueryRepository;
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
@DisplayName("CartItemQueryService 단위 테스트")
class CartItemQueryServiceTest {

	@Mock
	private CartItemQueryRepository cartItemQueryRepository;

	private CartItemQueryService cartItemQueryService;


	@BeforeEach
	void setUp() {
		cartItemQueryService = new CartItemQueryService(cartItemQueryRepository);
	}


	@Nested
	@DisplayName("getCartByUserId()")
	class GetCartByUserIdTest {

		@Test
		@DisplayName("[getCartByUserId()] 장바구니 항목 존재 -> 항목 목록 반환")
		void getCartByUserIdSuccess() {
			// Arrange
			Long userId = 1L;
			CartItem item1 = CartItem.reconstruct(1L, userId, 100L, Quantity.from(1L), true,
				LocalDateTime.now(), LocalDateTime.now());
			CartItem item2 = CartItem.reconstruct(2L, userId, 200L, Quantity.from(2L), true,
				LocalDateTime.now(), LocalDateTime.now());

			given(cartItemQueryRepository.findByUserId(userId)).willReturn(List.of(item1, item2));

			// Act
			List<CartItem> result = cartItemQueryService.getCartByUserId(userId);

			// Assert
			assertThat(result).hasSize(2);
		}


		@Test
		@DisplayName("[getCartByUserId()] 장바구니 비어있음 -> 빈 목록 반환")
		void getCartByUserIdEmpty() {
			// Arrange
			given(cartItemQueryRepository.findByUserId(999L)).willReturn(List.of());

			// Act
			List<CartItem> result = cartItemQueryService.getCartByUserId(999L);

			// Assert
			assertThat(result).isEmpty();
		}

	}


	@Nested
	@DisplayName("getCartStatus()")
	class GetCartStatusTest {

		@Test
		@DisplayName("[getCartStatus()] 3개 항목 중 2개 선택 -> totalCount=3, selectedCount=2")
		void getCartStatusSuccess() {
			// Arrange
			Long userId = 1L;
			CartItem item1 = CartItem.reconstruct(1L, userId, 100L, Quantity.from(1L), true,
				LocalDateTime.now(), LocalDateTime.now());
			CartItem item2 = CartItem.reconstruct(2L, userId, 200L, Quantity.from(2L), true,
				LocalDateTime.now(), LocalDateTime.now());
			CartItem item3 = CartItem.reconstruct(3L, userId, 300L, Quantity.from(3L), false,
				LocalDateTime.now(), LocalDateTime.now());

			given(cartItemQueryRepository.findByUserId(userId)).willReturn(List.of(item1, item2, item3));

			// Act
			CartStatusOutDto result = cartItemQueryService.getCartStatus(userId);

			// Assert
			assertAll(
				() -> assertThat(result.totalCount()).isEqualTo(3),
				() -> assertThat(result.selectedCount()).isEqualTo(2),
				() -> assertThat(result.items()).hasSize(3)
			);
		}


		@Test
		@DisplayName("[getCartStatus()] 장바구니 비어있음 -> totalCount=0, selectedCount=0")
		void getCartStatusEmpty() {
			// Arrange
			given(cartItemQueryRepository.findByUserId(999L)).willReturn(List.of());

			// Act
			CartStatusOutDto result = cartItemQueryService.getCartStatus(999L);

			// Assert
			assertAll(
				() -> assertThat(result.totalCount()).isEqualTo(0),
				() -> assertThat(result.selectedCount()).isEqualTo(0),
				() -> assertThat(result.items()).isEmpty()
			);
		}

	}


	@Nested
	@DisplayName("findSelectedByUserId()")
	class FindSelectedByUserIdTest {

		@Test
		@DisplayName("[findSelectedByUserId()] 선택된 항목만 조회 -> 선택된 항목 목록 반환")
		void findSelectedByUserIdSuccess() {
			// Arrange
			Long userId = 1L;
			CartItem selected = CartItem.reconstruct(1L, userId, 100L, Quantity.from(1L), true,
				LocalDateTime.now(), LocalDateTime.now());

			given(cartItemQueryRepository.findSelectedByUserId(userId)).willReturn(List.of(selected));

			// Act
			List<CartItem> result = cartItemQueryService.findSelectedByUserId(userId);

			// Assert
			assertAll(
				() -> assertThat(result).hasSize(1),
				() -> assertThat(result.get(0).isSelected()).isTrue()
			);
		}

	}

}
