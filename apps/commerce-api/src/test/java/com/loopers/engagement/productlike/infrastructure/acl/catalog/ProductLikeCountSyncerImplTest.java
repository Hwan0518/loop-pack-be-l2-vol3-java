package com.loopers.engagement.productlike.infrastructure.acl.catalog;


import com.loopers.catalog.product.application.facade.ProductLikeCountCommandFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("ProductLikeCountSyncerImpl 단위 테스트")
class ProductLikeCountSyncerImplTest {

	@Mock
	private ProductLikeCountCommandFacade productLikeCountCommandFacade;

	private ProductLikeCountSyncerImpl productLikeCountSyncerImpl;


	@BeforeEach
	void setUp() {
		productLikeCountSyncerImpl = new ProductLikeCountSyncerImpl(productLikeCountCommandFacade);
	}


	@Nested
	@DisplayName("increaseLikeCount()")
	class IncreaseLikeCountTest {

		@Test
		@DisplayName("[increaseLikeCount()] 상품 ID 전달 -> Provider Facade에 좋아요 수 증가 위임")
		void increaseLikeCountSuccess() {
			// Arrange
			willDoNothing().given(productLikeCountCommandFacade).increaseLikeCount(1L);

			// Act
			productLikeCountSyncerImpl.increaseLikeCount(1L);

			// Assert
			verify(productLikeCountCommandFacade).increaseLikeCount(1L);
		}

	}


	@Nested
	@DisplayName("decreaseLikeCount()")
	class DecreaseLikeCountTest {

		@Test
		@DisplayName("[decreaseLikeCount()] 상품 ID 전달 -> Provider Facade에 좋아요 수 감소 위임")
		void decreaseLikeCountSuccess() {
			// Arrange
			willDoNothing().given(productLikeCountCommandFacade).decreaseLikeCount(1L);

			// Act
			productLikeCountSyncerImpl.decreaseLikeCount(1L);

			// Assert
			verify(productLikeCountCommandFacade).decreaseLikeCount(1L);
		}

	}

}
