package com.loopers.cart.cart.application.service;


import com.loopers.cart.cart.application.dto.in.CartItemAddInDto;
import com.loopers.cart.cart.application.dto.in.CartItemSelectionInDto;
import com.loopers.cart.cart.application.dto.in.CartItemUpdateQuantityInDto;
import com.loopers.cart.cart.application.dto.out.CartItemSelectionOutDto;
import com.loopers.cart.cart.application.port.out.client.catalog.CartProductReader;
import com.loopers.cart.cart.application.port.out.client.user.UserAuthenticator;
import com.loopers.cart.cart.domain.model.CartItem;
import com.loopers.cart.cart.domain.model.vo.Quantity;
import com.loopers.cart.cart.domain.repository.CartItemCommandRepository;
import com.loopers.cart.cart.domain.repository.CartItemQueryRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("CartItemCommandService 단위 테스트")
class CartItemCommandServiceTest {

	@Mock
	private CartItemCommandRepository cartItemCommandRepository;

	@Mock
	private CartItemQueryRepository cartItemQueryRepository;

	@Mock
	private CartProductReader cartProductReader;

	@Mock
	private UserAuthenticator userAuthenticator;

	private CartItemCommandService cartItemCommandService;


	@BeforeEach
	void setUp() {
		cartItemCommandService = new CartItemCommandService(
			cartItemCommandRepository, cartItemQueryRepository, cartProductReader, userAuthenticator
		);
	}


	@Nested
	@DisplayName("addItem()")
	class AddItemTest {

		@Test
		@DisplayName("[addItem()] 신규 상품 추가 -> 새 CartItem 생성 및 저장")
		void addNewItem() {
			// Arrange
			Long userId = 1L;
			CartItemAddInDto inDto = new CartItemAddInDto(100L, 3L);
			CartItem savedItem = CartItem.reconstruct(1L, userId, 100L, Quantity.from(3L), true,
				LocalDateTime.now(), LocalDateTime.now());

			given(cartItemQueryRepository.findByUserIdAndProductId(userId, 100L))
				.willReturn(Optional.empty());
			given(cartItemCommandRepository.save(any(CartItem.class))).willReturn(savedItem);

			// Act
			CartItem result = cartItemCommandService.addItem(userId, inDto);

			// Assert
			assertAll(
				() -> assertThat(result.getId()).isEqualTo(1L),
				() -> assertThat(result.getQuantity().value()).isEqualTo(3L)
			);
		}


		@Test
		@DisplayName("[addItem()] 상품 미존재 -> CART_PRODUCT_NOT_FOUND 예외. Provider 예외(PRODUCT_NOT_FOUND)를 Service에서 매핑")
		void addItemProductNotFound() {
			// Arrange — Port는 Provider 원본 예외(PRODUCT_NOT_FOUND)를 전파
			Long userId = 1L;
			CartItemAddInDto inDto = new CartItemAddInDto(999L, 1L);

			willThrow(new CoreException(ErrorType.PRODUCT_NOT_FOUND))
				.given(cartProductReader).validateProductExists(999L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> cartItemCommandService.addItem(userId, inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.CART_PRODUCT_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.CART_PRODUCT_NOT_FOUND.getMessage())
			);
		}


		@Test
		@DisplayName("[addItem()] 기존 상품 추가 -> 수량 합산 후 저장")
		void addExistingItem() {
			// Arrange
			Long userId = 1L;
			CartItemAddInDto inDto = new CartItemAddInDto(100L, 5L);
			CartItem existingItem = CartItem.reconstruct(1L, userId, 100L, Quantity.from(3L), true,
				LocalDateTime.now(), LocalDateTime.now());
			CartItem updatedItem = CartItem.reconstruct(1L, userId, 100L, Quantity.from(8L), true,
				LocalDateTime.now(), LocalDateTime.now());

			given(cartItemQueryRepository.findByUserIdAndProductId(userId, 100L))
				.willReturn(Optional.of(existingItem));
			given(cartItemCommandRepository.save(any(CartItem.class))).willReturn(updatedItem);

			// Act
			CartItem result = cartItemCommandService.addItem(userId, inDto);

			// Assert
			assertThat(result.getQuantity().value()).isEqualTo(8L);
		}

	}


	@Nested
	@DisplayName("updateQuantity()")
	class UpdateQuantityTest {

		@Test
		@DisplayName("[updateQuantity()] 유효한 요청 -> 수량 변경 후 저장")
		void updateQuantitySuccess() {
			// Arrange
			Long cartItemId = 1L;
			Long userId = 1L;
			CartItemUpdateQuantityInDto inDto = new CartItemUpdateQuantityInDto(10L);
			CartItem existingItem = CartItem.reconstruct(cartItemId, userId, 100L, Quantity.from(3L), true,
				LocalDateTime.now(), LocalDateTime.now());
			CartItem updatedItem = CartItem.reconstruct(cartItemId, userId, 100L, Quantity.from(10L), true,
				LocalDateTime.now(), LocalDateTime.now());

			given(cartItemQueryRepository.findById(cartItemId)).willReturn(Optional.of(existingItem));
			given(cartItemCommandRepository.save(any(CartItem.class))).willReturn(updatedItem);

			// Act
			CartItem result = cartItemCommandService.updateQuantity(cartItemId, userId, inDto);

			// Assert
			assertThat(result.getQuantity().value()).isEqualTo(10L);
		}


		@Test
		@DisplayName("[updateQuantity()] 존재하지 않는 항목 -> CART_ITEM_NOT_FOUND 예외")
		void updateQuantityNotFound() {
			// Arrange
			given(cartItemQueryRepository.findById(999L)).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> cartItemCommandService.updateQuantity(999L, 1L, new CartItemUpdateQuantityInDto(10L)));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.CART_ITEM_NOT_FOUND);
		}


		@Test
		@DisplayName("[updateQuantity()] 다른 사용자의 항목 -> CART_ITEM_NOT_FOUND 예외 (보안)")
		void updateQuantityOwnershipFail() {
			// Arrange
			Long cartItemId = 1L;
			CartItem otherUserItem = CartItem.reconstruct(cartItemId, 2L, 100L, Quantity.from(3L), true,
				LocalDateTime.now(), LocalDateTime.now());
			given(cartItemQueryRepository.findById(cartItemId)).willReturn(Optional.of(otherUserItem));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> cartItemCommandService.updateQuantity(cartItemId, 1L, new CartItemUpdateQuantityInDto(10L)));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.CART_ITEM_NOT_FOUND);
		}

	}


	@Nested
	@DisplayName("deleteItem()")
	class DeleteItemTest {

		@Test
		@DisplayName("[deleteItem()] 유효한 요청 -> 장바구니 항목 삭제")
		void deleteItemSuccess() {
			// Arrange
			Long cartItemId = 1L;
			Long userId = 1L;
			CartItem cartItem = CartItem.reconstruct(cartItemId, userId, 100L, Quantity.from(3L), true,
				LocalDateTime.now(), LocalDateTime.now());

			given(cartItemQueryRepository.findById(cartItemId)).willReturn(Optional.of(cartItem));
			willDoNothing().given(cartItemCommandRepository).delete(cartItem);

			// Act
			cartItemCommandService.deleteItem(cartItemId, userId);

			// Assert
			verify(cartItemCommandRepository).delete(cartItem);
		}


		@Test
		@DisplayName("[deleteItem()] 존재하지 않는 항목 -> CART_ITEM_NOT_FOUND 예외")
		void deleteItemNotFound() {
			// Arrange
			given(cartItemQueryRepository.findById(999L)).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> cartItemCommandService.deleteItem(999L, 1L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.CART_ITEM_NOT_FOUND);
		}


		@Test
		@DisplayName("[deleteItem()] 다른 사용자의 항목 -> CART_ITEM_NOT_FOUND 예외 (보안)")
		void deleteItemOwnershipFail() {
			// Arrange
			CartItem otherUserItem = CartItem.reconstruct(1L, 2L, 100L, Quantity.from(3L), true,
				LocalDateTime.now(), LocalDateTime.now());
			given(cartItemQueryRepository.findById(1L)).willReturn(Optional.of(otherUserItem));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> cartItemCommandService.deleteItem(1L, 1L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.CART_ITEM_NOT_FOUND),
				() -> verify(cartItemCommandRepository, never()).delete(any())
			);
		}

	}


	@Nested
	@DisplayName("updateSelection()")
	class UpdateSelectionTest {

		@Test
		@DisplayName("[updateSelection()] 선택/해제 변경 -> 선택된 ID와 해제된 ID 목록 반환")
		void updateSelectionSuccess() {
			// Arrange
			Long userId = 1L;
			CartItem item1 = CartItem.reconstruct(1L, userId, 100L, Quantity.from(1L), true,
				LocalDateTime.now(), LocalDateTime.now());
			CartItem item2 = CartItem.reconstruct(2L, userId, 200L, Quantity.from(2L), true,
				LocalDateTime.now(), LocalDateTime.now());
			CartItem item3 = CartItem.reconstruct(3L, userId, 300L, Quantity.from(3L), true,
				LocalDateTime.now(), LocalDateTime.now());

			given(cartItemQueryRepository.findByUserId(userId)).willReturn(List.of(item1, item2, item3));
			given(cartItemCommandRepository.save(any(CartItem.class))).willAnswer(inv -> inv.getArgument(0));

			CartItemSelectionInDto inDto = new CartItemSelectionInDto(List.of(1L, 3L));

			// Act
			CartItemSelectionOutDto result = cartItemCommandService.updateSelection(userId, inDto);

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
		@DisplayName("[deleteAllByProductId()] 상품 ID로 삭제 -> repository 호출")
		void deleteAllByProductId() {
			// Arrange
			willDoNothing().given(cartItemCommandRepository).deleteAllByProductId(100L);

			// Act
			cartItemCommandService.deleteAllByProductId(100L);

			// Assert
			verify(cartItemCommandRepository).deleteAllByProductId(100L);
		}

	}


	@Nested
	@DisplayName("deleteAllByIds()")
	class DeleteAllByIdsTest {

		@Test
		@DisplayName("[deleteAllByIds()] ID 목록으로 삭제 -> repository 호출")
		void deleteAllByIds() {
			// Arrange
			List<Long> ids = List.of(1L, 2L);
			willDoNothing().given(cartItemCommandRepository).deleteAllByIds(ids);

			// Act
			cartItemCommandService.deleteAllByIds(ids);

			// Assert
			verify(cartItemCommandRepository).deleteAllByIds(ids);
		}

	}


	@Nested
	@DisplayName("deleteAllByUserIdAndIds()")
	class DeleteAllByUserIdAndIdsTest {

		@Test
		@DisplayName("[deleteAllByUserIdAndIds()] 사용자 ID와 ID 목록으로 삭제 -> repository에 userId와 ids 전달")
		void deleteAllByUserIdAndIds() {
			// Arrange
			Long userId = 1L;
			List<Long> ids = List.of(1L, 2L);
			willDoNothing().given(cartItemCommandRepository).deleteAllByUserIdAndIds(userId, ids);

			// Act
			cartItemCommandService.deleteAllByUserIdAndIds(userId, ids);

			// Assert
			verify(cartItemCommandRepository).deleteAllByUserIdAndIds(userId, ids);
		}

	}

}
