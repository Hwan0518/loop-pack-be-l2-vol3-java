package com.loopers.engagement.productlike.application.facade;


import com.loopers.engagement.productlike.application.dto.out.ProductLikeOutDto;
import com.loopers.engagement.productlike.application.service.ProductLikeCommandService;
import com.loopers.engagement.productlike.domain.event.ProductLikedEvent;
import com.loopers.engagement.productlike.domain.event.ProductUnlikedEvent;
import com.loopers.engagement.productlike.domain.model.ProductLike;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("ProductLikeCommandFacade 테스트")
class ProductLikeCommandFacadeTest {

	@Mock
	private ProductLikeCommandService productLikeCommandService;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private ProductLikeCommandFacade productLikeCommandFacade;


	@BeforeEach
	void setUp() {
		productLikeCommandFacade = new ProductLikeCommandFacade(
			productLikeCommandService, eventPublisher
		);
	}


	@Nested
	@DisplayName("createLike() - 상품 좋아요 생성")
	class CreateLikeTest {

		@Test
		@DisplayName("[createLike()] 신규 좋아요 -> OutDto 반환 + ProductLikedEvent 발행")
		void createLike() {
			// Arrange
			Long userId = 1L;
			Long targetId = 100L;
			ProductLike like = ProductLike.reconstruct(1L, userId, targetId, LocalDateTime.now());

			given(productLikeCommandService.findLike(userId, targetId)).willReturn(Optional.empty());
			given(productLikeCommandService.createLike(userId, targetId)).willReturn(like);

			// Act
			ProductLikeOutDto result = productLikeCommandFacade.createLike(userId, targetId);

			// Assert
			ArgumentCaptor<ProductLikedEvent> eventCaptor = ArgumentCaptor.forClass(ProductLikedEvent.class);
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.userId()).isEqualTo(1L),
				() -> assertThat(result.targetId()).isEqualTo(100L),
				() -> verify(productLikeCommandService).createLike(userId, targetId),
				() -> verify(eventPublisher).publishEvent(eventCaptor.capture())
			);

			ProductLikedEvent event = eventCaptor.getValue();
			assertAll(
				() -> assertThat(event.productLikeId()).isEqualTo(1L),
				() -> assertThat(event.userId()).isEqualTo(1L),
				() -> assertThat(event.productId()).isEqualTo(100L)
			);
		}

		@Test
		@DisplayName("[createLike()] 기존 좋아요 존재 -> 기존 반환 (멱등). 이벤트 미발행")
		void createLikeIdempotent() {
			// Arrange
			Long userId = 1L;
			Long targetId = 100L;
			ProductLike existingLike = ProductLike.reconstruct(1L, userId, targetId, LocalDateTime.now());

			given(productLikeCommandService.findLike(userId, targetId)).willReturn(Optional.of(existingLike));

			// Act
			ProductLikeOutDto result = productLikeCommandFacade.createLike(userId, targetId);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> verify(productLikeCommandService, never()).createLike(any(), any()),
				() -> verify(eventPublisher, never()).publishEvent(any())
			);
		}

	}


	@Nested
	@DisplayName("deleteLike() - 상품 좋아요 삭제")
	class DeleteLikeTest {

		@Test
		@DisplayName("[deleteLike()] 유효한 요청 -> 서비스 위임 + ProductUnlikedEvent 발행")
		void deleteLike() {
			// Arrange
			Long userId = 1L;
			Long targetId = 100L;

			// Act
			productLikeCommandFacade.deleteLike(userId, targetId);

			// Assert
			ArgumentCaptor<ProductUnlikedEvent> eventCaptor = ArgumentCaptor.forClass(ProductUnlikedEvent.class);
			verify(productLikeCommandService).deleteLike(userId, targetId);
			verify(eventPublisher).publishEvent(eventCaptor.capture());

			ProductUnlikedEvent event = eventCaptor.getValue();
			assertAll(
				() -> assertThat(event.userId()).isEqualTo(1L),
				() -> assertThat(event.productId()).isEqualTo(100L)
			);
		}
	}


	@Nested
	@DisplayName("deleteAllByProductId() - 상품 ID로 좋아요 전체 삭제")
	class DeleteAllByProductIdTest {

		@Test
		@DisplayName("[deleteAllByProductId()] 유효한 상품 ID -> 서비스 deleteAllByTargetId 위임")
		void deleteAllByProductId() {
			// Arrange
			// Act
			productLikeCommandFacade.deleteAllByProductId(100L);

			// Assert
			verify(productLikeCommandService).deleteAllByTargetId(100L);
		}
	}

}
