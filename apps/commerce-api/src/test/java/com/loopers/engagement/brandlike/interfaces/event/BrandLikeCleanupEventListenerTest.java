package com.loopers.engagement.brandlike.interfaces.event;


import com.loopers.catalog.brand.domain.event.BrandDeletedEvent;
import com.loopers.engagement.brandlike.application.service.BrandLikeCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("BrandLikeCleanupEventListener 테스트")
class BrandLikeCleanupEventListenerTest {

	@Mock
	private BrandLikeCommandService brandLikeCommandService;

	private BrandLikeCleanupEventListener brandLikeCleanupEventListener;


	@BeforeEach
	void setUp() {
		brandLikeCleanupEventListener = new BrandLikeCleanupEventListener(brandLikeCommandService);
	}


	@Test
	@DisplayName("[handleBrandDeleted()] 브랜드 삭제 이벤트 수신 -> 해당 브랜드의 좋아요 전체 삭제")
	void handleBrandDeleted() {
		// Arrange
		BrandDeletedEvent event = new BrandDeletedEvent(100L);
		willDoNothing().given(brandLikeCommandService).deleteAllByTargetId(100L);

		// Act
		brandLikeCleanupEventListener.handleBrandDeleted(event);

		// Assert
		verify(brandLikeCommandService).deleteAllByTargetId(100L);
	}

}
