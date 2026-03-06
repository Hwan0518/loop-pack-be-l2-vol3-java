package com.loopers.ordering.order.infrastructure.acl.catalog;


import com.loopers.catalog.product.application.facade.ProductQueryFacade;
import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.model.vo.Money;
import com.loopers.catalog.product.domain.model.vo.ProductName;
import com.loopers.catalog.product.domain.model.vo.Stock;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("OrderProductReaderImpl 단위 테스트")
class OrderProductReaderImplTest {

	@Mock
	private ProductQueryFacade productQueryFacade;

	private OrderProductReaderImpl orderProductReaderImpl;


	@BeforeEach
	void setUp() {
		orderProductReaderImpl = new OrderProductReaderImpl(productQueryFacade);
	}


	@Test
	@DisplayName("[readProducts()] 상품 ID 목록 -> OrderProductInfo 목록 반환. batch 조회 후 변환")
	void readProductsSuccess() {
		// Arrange
		List<Long> productIds = List.of(1L, 2L);
		Product p1 = Product.reconstruct(1L, 1L,
			ProductName.from("나이키 에어맥스"),
			Money.from(new BigDecimal("100000")),
			Stock.from(10L),
			null, 0L, null, null);
		Product p2 = Product.reconstruct(2L, 1L,
			ProductName.from("아디다스 울트라부스트"),
			Money.from(new BigDecimal("200000")),
			Stock.from(5L),
			null, 0L, null, null);
		given(productQueryFacade.findActiveByIds(productIds)).willReturn(List.of(p1, p2));

		// Act
		List<OrderProductInfo> result = orderProductReaderImpl.readProducts(productIds);

		// Assert
		assertAll(
			() -> assertThat(result).hasSize(2),
			() -> assertThat(result.get(0).productId()).isEqualTo(1L),
			() -> assertThat(result.get(0).name()).isEqualTo("나이키 에어맥스"),
			() -> assertThat(result.get(1).productId()).isEqualTo(2L),
			() -> verify(productQueryFacade).findActiveByIds(productIds)
		);
	}


	@Test
	@DisplayName("[readProducts()] 빈 ID 목록 -> 빈 목록 반환")
	void readProductsEmptyIds() {
		// Arrange
		given(productQueryFacade.findActiveByIds(List.of())).willReturn(List.of());

		// Act
		List<OrderProductInfo> result = orderProductReaderImpl.readProducts(List.of());

		// Assert
		assertThat(result).isEmpty();
	}

}
