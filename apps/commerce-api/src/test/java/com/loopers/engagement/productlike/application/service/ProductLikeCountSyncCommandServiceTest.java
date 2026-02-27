package com.loopers.engagement.productlike.application.service;


import com.loopers.engagement.productlike.application.port.out.client.catalog.ProductLikeCountSyncer;
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
@DisplayName("ProductLikeCountSyncCommandService 테스트")
class ProductLikeCountSyncCommandServiceTest {

	@Mock
	private ProductLikeCountSyncer productLikeCountSyncer;

	private ProductLikeCountSyncCommandService productLikeCountSyncCommandService;


	@BeforeEach
	void setUp() {
		productLikeCountSyncCommandService = new ProductLikeCountSyncCommandService(productLikeCountSyncer);
	}


	@Nested
	@DisplayName("increaseLikeCount() - 좋아요 수 증가")
	class IncreaseLikeCountTest {

		@Test
		@DisplayName("[increaseLikeCount()] 유효한 상품 ID -> ProductLikeCountSyncer에 위임")
		void increaseLikeCountSuccess() {
			// Arrange
			willDoNothing().given(productLikeCountSyncer).increaseLikeCount(1L);

			// Act
			productLikeCountSyncCommandService.increaseLikeCount(1L);

			// Assert
			verify(productLikeCountSyncer).increaseLikeCount(1L);
		}

	}


	@Nested
	@DisplayName("decreaseLikeCount() - 좋아요 수 감소")
	class DecreaseLikeCountTest {

		@Test
		@DisplayName("[decreaseLikeCount()] 유효한 상품 ID -> ProductLikeCountSyncer에 위임")
		void decreaseLikeCountSuccess() {
			// Arrange
			willDoNothing().given(productLikeCountSyncer).decreaseLikeCount(1L);

			// Act
			productLikeCountSyncCommandService.decreaseLikeCount(1L);

			// Assert
			verify(productLikeCountSyncer).decreaseLikeCount(1L);
		}

	}

}
