package com.loopers.catalog.product.application.facade;


import com.loopers.catalog.product.application.service.ProductCommandService;
import com.loopers.catalog.product.application.service.ProductQueryService;
import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.model.vo.Money;
import com.loopers.catalog.product.domain.model.vo.ProductName;
import com.loopers.catalog.product.domain.model.vo.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("ProductLikeCountCommandFacade 단위 테스트")
class ProductLikeCountCommandFacadeTest {

	@Mock
	private ProductCommandService productCommandService;
	@Mock
	private ProductQueryService productQueryService;

	private ProductLikeCountCommandFacade productLikeCountCommandFacade;


	@BeforeEach
	void setUp() {
		productLikeCountCommandFacade = new ProductLikeCountCommandFacade(
			productCommandService, productQueryService
		);
	}


	private Product createTestProduct() {
		return Product.reconstruct(1L, 1L,
			ProductName.from("테스트 상품"),
			Money.from(new BigDecimal("10000")),
			Stock.from(100L),
			null, 0L, null);
	}


	@Nested
	@DisplayName("increaseLikeCount()")
	class IncreaseLikeCountTest {

		@Test
		@DisplayName("[increaseLikeCount()] 활성 상품 ID -> 좋아요 수 증가. QueryService로 조회 후 CommandService로 증가")
		void increaseLikeCountSuccess() {
			// Arrange
			Product product = createTestProduct();
			given(productQueryService.findActiveById(1L)).willReturn(product);

			// Act
			productLikeCountCommandFacade.increaseLikeCount(1L);

			// Assert
			assertAll(
				() -> verify(productQueryService).findActiveById(1L),
				() -> verify(productCommandService).increaseLikeCount(product)
			);
		}

	}


	@Nested
	@DisplayName("decreaseLikeCount()")
	class DecreaseLikeCountTest {

		@Test
		@DisplayName("[decreaseLikeCount()] 활성 상품 ID -> 좋아요 수 감소. QueryService로 조회 후 CommandService로 감소")
		void decreaseLikeCountSuccess() {
			// Arrange
			Product product = createTestProduct();
			given(productQueryService.findActiveById(1L)).willReturn(product);

			// Act
			productLikeCountCommandFacade.decreaseLikeCount(1L);

			// Assert
			assertAll(
				() -> verify(productQueryService).findActiveById(1L),
				() -> verify(productCommandService).decreaseLikeCount(product)
			);
		}

	}

}
