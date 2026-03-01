package com.loopers.ordering.order.application.service;


import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemInfo;
import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemReader;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductInfo;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductReader;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderStockManager;
import com.loopers.ordering.order.application.port.out.client.user.UserAuthenticator;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCheckoutCommandService 테스트")
class OrderCheckoutCommandServiceTest {

	@Mock
	private OrderCartItemReader orderCartItemReader;

	@Mock
	private OrderProductReader orderProductReader;

	@Mock
	private OrderStockManager orderStockManager;

	@Mock
	private UserAuthenticator userAuthenticator;

	private OrderCheckoutCommandService orderCheckoutCommandService;

	@BeforeEach
	void setUp() {
		orderCheckoutCommandService = new OrderCheckoutCommandService(
			orderCartItemReader, orderProductReader, orderStockManager, userAuthenticator
		);
	}


	@Nested
	@DisplayName("readCartItemsByIds() 테스트")
	class ReadCartItemsByIdsTest {

		@Test
		@DisplayName("[readCartItemsByIds()] 장바구니 항목 ID 목록으로 조회 -> 항목 목록 반환")
		void readCartItemsByIdsSuccess() {
			// Arrange
			Long userId = 1L;
			List<Long> cartItemIds = List.of(100L, 101L);
			List<OrderCartItemInfo> cartItems = List.of(
				new OrderCartItemInfo(100L, 1L, 2L),
				new OrderCartItemInfo(101L, 2L, 1L)
			);
			given(orderCartItemReader.readCartItemsByIds(userId, cartItemIds)).willReturn(cartItems);

			// Act
			List<OrderCartItemInfo> result = orderCheckoutCommandService.readCartItemsByIds(userId, cartItemIds);

			// Assert
			assertThat(result).hasSize(2);
		}

		@Test
		@DisplayName("[readCartItemsByIds()] 조회 결과가 비어있음 -> EMPTY_CART 예외")
		void readCartItemsByIdsEmpty() {
			// Arrange
			Long userId = 1L;
			List<Long> cartItemIds = List.of(999L);
			given(orderCartItemReader.readCartItemsByIds(userId, cartItemIds)).willReturn(Collections.emptyList());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> orderCheckoutCommandService.readCartItemsByIds(userId, cartItemIds));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.EMPTY_CART),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.EMPTY_CART.getMessage())
			);
		}
	}


	@Nested
	@DisplayName("readProducts() 테스트")
	class ReadProductsTest {

		@Test
		@DisplayName("[readProducts()] 상품 ID 목록 -> 상품 정보 목록 반환")
		void readProductsSuccess() {
			// Arrange
			List<Long> productIds = List.of(1L, 2L);
			List<OrderProductInfo> products = List.of(
				new OrderProductInfo(1L, "나이키 에어맥스", new BigDecimal("100000"), 10L),
				new OrderProductInfo(2L, "아디다스 울트라부스트", new BigDecimal("200000"), 5L)
			);
			given(orderProductReader.readProducts(productIds)).willReturn(products);

			// Act
			List<OrderProductInfo> result = orderCheckoutCommandService.readProducts(productIds);

			// Assert
			assertThat(result).hasSize(2);
		}


		@Test
		@DisplayName("[readProducts()] 요청한 상품 수 != 조회 결과 수 -> PRODUCT_NOT_FOUND 예외. "
			+ "일부 상품이 삭제/미존재 시 건수 불일치 검증 실패")
		void readProductsPartialNotFound() {
			// Arrange
			List<Long> productIds = List.of(1L, 2L);
			List<OrderProductInfo> products = List.of(
				new OrderProductInfo(1L, "나이키 에어맥스", new BigDecimal("100000"), 10L)
			);  // 2개 요청 → 1개만 반환
			given(orderProductReader.readProducts(productIds)).willReturn(products);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> orderCheckoutCommandService.readProducts(productIds));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND.getMessage())
			);
		}
	}


	@Nested
	@DisplayName("decreaseStocks() 테스트")
	class DecreaseStocksTest {

		@Test
		@DisplayName("[decreaseStocks()] 장바구니 항목별 재고 차감. productId 오름차순 정렬 후 처리")
		void decreaseStocksSuccess() {
			// Arrange
			List<OrderCartItemInfo> cartItems = List.of(
				new OrderCartItemInfo(101L, 3L, 1L),
				new OrderCartItemInfo(100L, 1L, 2L)
			);

			// Act
			orderCheckoutCommandService.decreaseStocks(cartItems);

			// Assert — productId 오름차순 정렬 확인 (1L → 3L)
			InOrder inOrder = inOrder(orderStockManager);
			inOrder.verify(orderStockManager).decreaseStock(1L, 2L);
			inOrder.verify(orderStockManager).decreaseStock(3L, 1L);
		}

		@Test
		@DisplayName("[decreaseStocks()] 중복 productId 수량 합산 후 1회만 차감")
		void decreaseStocksDuplicateAggregation() {
			// Arrange
			List<OrderCartItemInfo> cartItems = List.of(
				new OrderCartItemInfo(100L, 1L, 3L),
				new OrderCartItemInfo(101L, 1L, 2L)
			);

			// Act
			orderCheckoutCommandService.decreaseStocks(cartItems);

			// Assert — productId 1L은 수량 합산(3+2=5)으로 1회만 호출
			verify(orderStockManager).decreaseStock(1L, 5L);
		}

		@Test
		@DisplayName("[decreaseStocks()] 재고 부족 -> PRODUCT_OUT_OF_STOCK 예외. 재고가 부족한 상품의 재고 차감 시 예외 발생")
		void decreaseStocksOutOfStock() {
			// Arrange
			List<OrderCartItemInfo> cartItems = List.of(
				new OrderCartItemInfo(100L, 1L, 100L)
			);
			willThrow(new CoreException(ErrorType.PRODUCT_OUT_OF_STOCK))
				.given(orderStockManager).decreaseStock(1L, 100L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> orderCheckoutCommandService.decreaseStocks(cartItems));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_OUT_OF_STOCK),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PRODUCT_OUT_OF_STOCK.getMessage())
			);
		}
	}

}
