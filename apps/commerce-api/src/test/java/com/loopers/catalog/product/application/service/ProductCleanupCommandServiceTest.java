package com.loopers.catalog.product.application.service;


import com.loopers.catalog.product.application.port.out.client.cart.CartItemCleanupManager;
import com.loopers.catalog.product.application.port.out.client.engagement.ProductLikeCleanupManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("ProductCleanupCommandService 테스트")
class ProductCleanupCommandServiceTest {

	@Mock
	private ProductLikeCleanupManager productLikeCleanupManager;

	@Mock
	private CartItemCleanupManager cartItemCleanupManager;

	private ProductCleanupCommandService productCleanupCommandService;


	@BeforeEach
	void setUp() {
		productCleanupCommandService = new ProductCleanupCommandService(
			productLikeCleanupManager, cartItemCleanupManager
		);
	}


	@Nested
	@DisplayName("deleteAllProductLikes() - 상품 좋아요 전체 삭제")
	class DeleteAllProductLikesTest {

		@Test
		@DisplayName("[deleteAllProductLikes()] 유효한 상품 ID -> ProductLikeCleanupManager에 위임")
		void deleteAllProductLikesSuccess() {
			// Arrange
			willDoNothing().given(productLikeCleanupManager).deleteAllByProductId(1L);

			// Act
			productCleanupCommandService.deleteAllProductLikes(1L);

			// Assert
			verify(productLikeCleanupManager).deleteAllByProductId(1L);
		}

	}


	@Nested
	@DisplayName("deleteAllCartItems() - 장바구니 항목 전체 삭제")
	class DeleteAllCartItemsTest {

		@Test
		@DisplayName("[deleteAllCartItems()] 유효한 상품 ID -> CartItemCleanupManager에 위임")
		void deleteAllCartItemsSuccess() {
			// Arrange
			willDoNothing().given(cartItemCleanupManager).deleteAllByProductId(1L);

			// Act
			productCleanupCommandService.deleteAllCartItems(1L);

			// Assert
			verify(cartItemCleanupManager).deleteAllByProductId(1L);
		}

	}

}
