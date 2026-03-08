package com.loopers.cart.cart.application.facade;


import com.loopers.cart.cart.application.dto.in.CartItemAddInDto;
import com.loopers.cart.cart.application.dto.in.CartItemSelectionInDto;
import com.loopers.cart.cart.application.dto.in.CartItemUpdateQuantityInDto;
import com.loopers.cart.cart.application.dto.out.CartItemOutDto;
import com.loopers.cart.cart.application.dto.out.CartItemSelectionOutDto;
import com.loopers.cart.cart.application.service.CartItemCommandService;
import com.loopers.cart.cart.domain.model.CartItem;
import com.loopers.cart.cart.domain.model.vo.Quantity;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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

			given(cartItemCommandService.addItem(eq(USER_ID), any(CartItemAddInDto.class))).willReturn(savedItem);

			// Act
			CartItemOutDto result = cartItemCommandFacade.addItem(USER_ID, inDto);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.productId()).isEqualTo(100L),
				() -> assertThat(result.quantity()).isEqualTo(3L)
			);
		}


		@Test
		@DisplayName("[recoverAddItemConflict()] @Retryable 재시도 소진 후 @Recover 호출 -> CART_ADD_CONFLICT 예외. "
			+ "INSERT UNIQUE 위반(user_id, product_id)이 재시도로도 해소되지 않는 극단적 경우의 안전망")
		void recoverAddItemConflictThrowsCartAddConflict() {
			// Arrange
			CartItemAddInDto inDto = new CartItemAddInDto(100L, 3L);
			DataIntegrityViolationException cause = new DataIntegrityViolationException("Unique constraint violated");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> cartItemCommandFacade.recoverAddItemConflict(cause, USER_ID, inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.CART_ADD_CONFLICT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.CART_ADD_CONFLICT.getMessage())
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

			given(cartItemCommandService.updateQuantity(eq(cartItemId), eq(USER_ID), any(CartItemUpdateQuantityInDto.class)))
				.willReturn(updatedItem);

			// Act
			CartItemOutDto result = cartItemCommandFacade.updateQuantity(USER_ID, cartItemId, inDto);

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
			willDoNothing().given(cartItemCommandService).deleteItem(1L, USER_ID);

			// Act
			cartItemCommandFacade.deleteItem(USER_ID, 1L);

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

			given(cartItemCommandService.updateSelection(eq(USER_ID), any(CartItemSelectionInDto.class)))
				.willReturn(outDto);

			// Act
			CartItemSelectionOutDto result = cartItemCommandFacade.updateSelection(USER_ID, inDto);

			// Assert
			assertAll(
				() -> assertThat(result.selectedIds()).containsExactlyInAnyOrder(1L, 3L),
				() -> assertThat(result.deselectedIds()).containsExactly(2L)
			);
		}

	}


	@Nested
	@DisplayName("deleteAllByProductId()")
	class DeleteAllByProductIdTest {

		@Test
		@DisplayName("[deleteAllByProductId()] 유효한 상품 ID -> 서비스 deleteAllByProductId 위임")
		void deleteAllByProductIdSuccess() {
			// Arrange
			willDoNothing().given(cartItemCommandService).deleteAllByProductId(100L);

			// Act
			cartItemCommandFacade.deleteAllByProductId(100L);

			// Assert
			verify(cartItemCommandService).deleteAllByProductId(100L);
		}

	}


	@Nested
	@DisplayName("deleteAllByUserIdAndIds()")
	class DeleteAllByUserIdAndIdsTest {

		@Test
		@DisplayName("[deleteAllByUserIdAndIds()] 유효한 사용자 ID + ID 목록 -> 서비스 deleteAllByUserIdAndIds 위임")
		void deleteAllByUserIdAndIdsSuccess() {
			// Arrange
			List<Long> ids = List.of(1L, 2L, 3L);
			willDoNothing().given(cartItemCommandService).deleteAllByUserIdAndIds(USER_ID, ids);

			// Act
			cartItemCommandFacade.deleteAllByUserIdAndIds(USER_ID, ids);

			// Assert
			verify(cartItemCommandService).deleteAllByUserIdAndIds(USER_ID, ids);
		}

	}

}
