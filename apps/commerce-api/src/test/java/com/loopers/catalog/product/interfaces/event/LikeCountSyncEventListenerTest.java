package com.loopers.catalog.product.interfaces.event;


import com.loopers.catalog.product.application.facade.ProductCommandFacade;
import com.loopers.engagement.productlike.domain.event.ProductLikeCancelledEvent;
import com.loopers.engagement.productlike.domain.event.ProductLikeCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("LikeCountSyncEventListener 단위 테스트")
class LikeCountSyncEventListenerTest {

	@Mock
	private ProductCommandFacade productCommandFacade;

	private LikeCountSyncEventListener likeCountSyncEventListener;


	@BeforeEach
	void setUp() {
		likeCountSyncEventListener = new LikeCountSyncEventListener(productCommandFacade);
	}


	@Nested
	@DisplayName("handleProductLikeCreated() 테스트")
	class HandleProductLikeCreatedTest {

		@Test
		@DisplayName("[handleProductLikeCreated()] ProductLikeCreatedEvent 수신 -> 좋아요 수 증가. Facade.increaseLikeCount() 호출")
		void handleProductLikeCreated() {
			// Arrange
			ProductLikeCreatedEvent event = new ProductLikeCreatedEvent(1L);

			// Act
			likeCountSyncEventListener.handleProductLikeCreated(event);

			// Assert
			verify(productCommandFacade).increaseLikeCount(1L);
		}

	}


	@Nested
	@DisplayName("handleProductLikeCancelled() 테스트")
	class HandleProductLikeCancelledTest {

		@Test
		@DisplayName("[handleProductLikeCancelled()] ProductLikeCancelledEvent 수신 -> 좋아요 수 감소. Facade.decreaseLikeCount() 호출")
		void handleProductLikeCancelled() {
			// Arrange
			ProductLikeCancelledEvent event = new ProductLikeCancelledEvent(1L);

			// Act
			likeCountSyncEventListener.handleProductLikeCancelled(event);

			// Assert
			verify(productCommandFacade).decreaseLikeCount(1L);
		}

	}

}
