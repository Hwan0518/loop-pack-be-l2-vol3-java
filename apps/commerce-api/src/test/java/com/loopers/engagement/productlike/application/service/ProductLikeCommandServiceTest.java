package com.loopers.engagement.productlike.application.service;


import com.loopers.engagement.productlike.application.port.out.client.catalog.ProductLikeTargetValidator;
import com.loopers.engagement.productlike.application.port.out.client.user.UserAuthenticator;
import com.loopers.engagement.productlike.domain.event.ProductLikeCancelledEvent;
import com.loopers.engagement.productlike.domain.event.ProductLikeCreatedEvent;
import com.loopers.engagement.productlike.domain.model.ProductLike;
import com.loopers.engagement.productlike.domain.repository.ProductLikeCommandRepository;
import com.loopers.engagement.productlike.domain.repository.ProductLikeQueryRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("ProductLikeCommandService 테스트")
class ProductLikeCommandServiceTest {

	@Mock
	private ProductLikeCommandRepository productLikeCommandRepository;
	@Mock
	private ProductLikeQueryRepository productLikeQueryRepository;
	@Mock
	private ProductLikeTargetValidator productLikeTargetValidator;
	@Mock
	private UserAuthenticator userAuthenticator;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	private ProductLikeCommandService productLikeCommandService;


	@BeforeEach
	void setUp() {
		productLikeCommandService = new ProductLikeCommandService(
			productLikeCommandRepository,
			productLikeQueryRepository,
			productLikeTargetValidator,
			userAuthenticator,
			eventPublisher
		);
	}


	@Nested
	@DisplayName("createLike() - 상품 좋아요 생성")
	class CreateLikeTest {

		@Test
		@DisplayName("[createLike()] 신규 좋아요 -> 저장 후 반환. ProductLikeCreatedEvent 발행")
		void createLikeSuccess() {
			// Arrange
			Long userId = 1L;
			Long targetId = 100L;
			ProductLike savedLike = ProductLike.reconstruct(1L, userId, targetId, LocalDateTime.now());

			willDoNothing().given(productLikeTargetValidator).validate(targetId);
			given(productLikeQueryRepository.findByUserIdAndTargetId(userId, targetId))
				.willReturn(Optional.empty());
			given(productLikeCommandRepository.save(any(ProductLike.class))).willReturn(savedLike);

			// Act
			ProductLike result = productLikeCommandService.createLike(userId, targetId);

			// Assert
			assertThat(result.getId()).isEqualTo(1L);
			verify(eventPublisher).publishEvent(new ProductLikeCreatedEvent(targetId));
		}

		@Test
		@DisplayName("[createLike()] 기존 좋아요 존재 -> 기존 좋아요 반환 (멱등). 이벤트 미발행")
		void createLikeIdempotent() {
			// Arrange
			Long userId = 1L;
			Long targetId = 100L;
			ProductLike existingLike = ProductLike.reconstruct(1L, userId, targetId, LocalDateTime.now());

			willDoNothing().given(productLikeTargetValidator).validate(targetId);
			given(productLikeQueryRepository.findByUserIdAndTargetId(userId, targetId))
				.willReturn(Optional.of(existingLike));

			// Act
			ProductLike result = productLikeCommandService.createLike(userId, targetId);

			// Assert
			assertThat(result.getId()).isEqualTo(1L);
			verify(productLikeCommandRepository, never()).save(any());
			verify(eventPublisher, never()).publishEvent(any());
		}

		@Test
		@DisplayName("[createLike()] 대상 상품 미존재 -> LIKE_TARGET_NOT_FOUND 예외. Provider 예외(PRODUCT_NOT_FOUND)를 Service에서 매핑")
		void createLikeTargetNotFound() {
			// Arrange — Port는 Provider 원본 예외(PRODUCT_NOT_FOUND)를 전파
			willThrow(new CoreException(ErrorType.PRODUCT_NOT_FOUND))
				.given(productLikeTargetValidator).validate(999L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> productLikeCommandService.createLike(1L, 999L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.LIKE_TARGET_NOT_FOUND);
		}
	}


	@Nested
	@DisplayName("deleteLike() - 상품 좋아요 삭제")
	class DeleteLikeTest {

		@Test
		@DisplayName("[deleteLike()] 좋아요 존재 -> 삭제 성공. ProductLikeCancelledEvent 발행")
		void deleteLikeSuccess() {
			// Arrange
			Long userId = 1L;
			Long targetId = 100L;
			ProductLike existingLike = ProductLike.reconstruct(1L, userId, targetId, LocalDateTime.now());

			given(productLikeQueryRepository.findByUserIdAndTargetId(userId, targetId))
				.willReturn(Optional.of(existingLike));
			willDoNothing().given(productLikeCommandRepository).delete(existingLike);

			// Act
			productLikeCommandService.deleteLike(userId, targetId);

			// Assert
			verify(productLikeCommandRepository).delete(existingLike);
			verify(eventPublisher).publishEvent(new ProductLikeCancelledEvent(targetId));
		}

		@Test
		@DisplayName("[deleteLike()] 좋아요 미존재 -> LIKE_NOT_FOUND 예외")
		void deleteLikeNotFound() {
			// Arrange
			given(productLikeQueryRepository.findByUserIdAndTargetId(1L, 100L))
				.willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> productLikeCommandService.deleteLike(1L, 100L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.LIKE_NOT_FOUND);
		}
	}


	@Nested
	@DisplayName("deleteAllByTargetId() - 상품 ID로 전체 삭제")
	class DeleteAllByTargetIdTest {

		@Test
		@DisplayName("[deleteAllByTargetId()] 삭제 호출 -> repository 위임")
		void deleteAllByTargetId() {
			// Arrange
			willDoNothing().given(productLikeCommandRepository).deleteAllByTargetId(100L);

			// Act
			productLikeCommandService.deleteAllByTargetId(100L);

			// Assert
			verify(productLikeCommandRepository).deleteAllByTargetId(100L);
		}
	}

}
