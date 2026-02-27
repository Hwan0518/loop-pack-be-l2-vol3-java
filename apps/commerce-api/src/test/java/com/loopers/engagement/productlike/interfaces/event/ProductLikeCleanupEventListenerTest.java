package com.loopers.engagement.productlike.interfaces.event;


import com.loopers.catalog.product.domain.event.ProductDeletedEvent;
import com.loopers.engagement.productlike.application.service.ProductLikeCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("ProductLikeCleanupEventListener 테스트")
class ProductLikeCleanupEventListenerTest {

	@Mock
	private ProductLikeCommandService productLikeCommandService;

	private ProductLikeCleanupEventListener productLikeCleanupEventListener;


	@BeforeEach
	void setUp() {
		productLikeCleanupEventListener = new ProductLikeCleanupEventListener(productLikeCommandService);
	}


	@Test
	@DisplayName("[handleProductDeleted()] 상품 삭제 이벤트 수신 -> 해당 상품의 좋아요 전체 삭제")
	void handleProductDeleted() {
		// Arrange
		ProductDeletedEvent event = new ProductDeletedEvent(100L);
		willDoNothing().given(productLikeCommandService).deleteAllByTargetId(100L);

		// Act
		productLikeCleanupEventListener.handleProductDeleted(event);

		// Assert
		verify(productLikeCommandService).deleteAllByTargetId(100L);
	}

}
