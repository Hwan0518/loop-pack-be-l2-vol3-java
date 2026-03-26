package com.loopers.engagement.productlike.interfaces.event;


import com.loopers.engagement.productlike.application.service.ProductLikeCommandService;
import com.loopers.engagement.productlike.domain.event.ProductLikedEvent;
import com.loopers.engagement.productlike.domain.event.ProductUnlikedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("ProductLikeEventListener 테스트")
class ProductLikeEventListenerTest {

	@Mock
	private ProductLikeCommandService productLikeCommandService;

	private ProductLikeEventListener productLikeEventListener;


	@BeforeEach
	void setUp() {
		productLikeEventListener = new ProductLikeEventListener(productLikeCommandService);
	}


	@Test
	@DisplayName("[handleLikeCountIncrease()] ProductLikedEvent 수신 -> likeCount 증가 위임")
	void handleLikeCountIncreaseSuccess() {
		// Arrange
		ProductLikedEvent event = new ProductLikedEvent(1L, 10L, 100L, LocalDateTime.now());

		// Act
		productLikeEventListener.handleLikeCountIncrease(event);

		// Assert
		verify(productLikeCommandService).increaseLikeCount(100L);
	}


	@Test
	@DisplayName("[handleLikeCountIncrease()] likeCount 증가 실패 -> 예외 무시 (로깅만)")
	void handleLikeCountIncreaseFailure() {
		// Arrange
		ProductLikedEvent event = new ProductLikedEvent(1L, 10L, 100L, LocalDateTime.now());
		willThrow(new RuntimeException("증가 실패")).given(productLikeCommandService).increaseLikeCount(100L);

		// Act — 예외가 전파되지 않음
		productLikeEventListener.handleLikeCountIncrease(event);

		// Assert
		verify(productLikeCommandService).increaseLikeCount(100L);
	}


	@Test
	@DisplayName("[handleLikeCountDecrease()] ProductUnlikedEvent 수신 -> likeCount 감소 위임")
	void handleLikeCountDecreaseSuccess() {
		// Arrange
		ProductUnlikedEvent event = new ProductUnlikedEvent(10L, 100L, LocalDateTime.now());

		// Act
		productLikeEventListener.handleLikeCountDecrease(event);

		// Assert
		verify(productLikeCommandService).decreaseLikeCount(100L);
	}


	@Test
	@DisplayName("[handleLikeCountDecrease()] likeCount 감소 실패 -> 예외 무시 (로깅만)")
	void handleLikeCountDecreaseFailure() {
		// Arrange
		ProductUnlikedEvent event = new ProductUnlikedEvent(10L, 100L, LocalDateTime.now());
		willThrow(new RuntimeException("감소 실패")).given(productLikeCommandService).decreaseLikeCount(100L);

		// Act — 예외가 전파되지 않음
		productLikeEventListener.handleLikeCountDecrease(event);

		// Assert
		verify(productLikeCommandService).decreaseLikeCount(100L);
	}

}
