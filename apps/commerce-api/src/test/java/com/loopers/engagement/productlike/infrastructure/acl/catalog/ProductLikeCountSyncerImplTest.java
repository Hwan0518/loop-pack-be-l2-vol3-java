package com.loopers.engagement.productlike.infrastructure.acl.catalog;


import com.loopers.catalog.product.application.facade.ProductCommandFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("ProductLikeCountSyncerImpl 단위 테스트")
class ProductLikeCountSyncerImplTest {

	@Mock
	private ProductCommandFacade productCommandFacade;

	private ProductLikeCountSyncerImpl productLikeCountSyncerImpl;


	@BeforeEach
	void setUp() {
		productLikeCountSyncerImpl = new ProductLikeCountSyncerImpl(productCommandFacade);
	}


	@Nested
	@DisplayName("increaseLikeCount()")
	class IncreaseLikeCountTest {

		@Test
		@DisplayName("[increaseLikeCount()] 상품 ID 전달 -> Provider Facade에 동일한 상품 ID로 좋아요 수 증가 위임")
		void increaseLikeCountSuccess() {
			// Arrange
			Long productId = 42L;

			// Act
			productLikeCountSyncerImpl.increaseLikeCount(productId);

			// Assert — 전달된 상품 ID가 정확히 위임됨을 검증
			ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
			verify(productCommandFacade).increaseLikeCount(captor.capture());
			assertThat(captor.getValue()).isEqualTo(productId);
		}

	}


	@Nested
	@DisplayName("decreaseLikeCount()")
	class DecreaseLikeCountTest {

		@Test
		@DisplayName("[decreaseLikeCount()] 상품 ID 전달 -> Provider Facade에 동일한 상품 ID로 좋아요 수 감소 위임")
		void decreaseLikeCountSuccess() {
			// Arrange
			Long productId = 42L;

			// Act
			productLikeCountSyncerImpl.decreaseLikeCount(productId);

			// Assert — 전달된 상품 ID가 정확히 위임됨을 검증
			ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
			verify(productCommandFacade).decreaseLikeCount(captor.capture());
			assertThat(captor.getValue()).isEqualTo(productId);
		}

	}

}
