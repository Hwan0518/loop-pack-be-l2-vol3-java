package com.loopers.engagement.productlike.application.service;


import com.loopers.engagement.productlike.application.port.out.client.catalog.ProductLikeCountSyncer;
import com.loopers.engagement.productlike.application.port.out.client.catalog.ProductLikeTargetValidator;
import com.loopers.engagement.productlike.domain.model.ProductLike;
import com.loopers.engagement.productlike.domain.repository.ProductLikeCommandRepository;
import com.loopers.engagement.productlike.domain.repository.ProductLikeQueryRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.support.common.outbox.application.port.OutboxEventPort;
import com.loopers.support.common.outbox.application.util.JsonSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
	private ProductLikeCountSyncer productLikeCountSyncer;
	@Mock
	private OutboxEventPort outboxEventPort;
	@Mock
	private JsonSerializer jsonSerializer;

	private ProductLikeCommandService productLikeCommandService;


	@BeforeEach
	void setUp() {
		productLikeCommandService = new ProductLikeCommandService(
			productLikeCommandRepository,
			productLikeQueryRepository,
			productLikeTargetValidator,
			productLikeCountSyncer,
			outboxEventPort,
			jsonSerializer
		);
	}


	@Nested
	@DisplayName("findLike() - 좋아요 조회")
	class FindLikeTest {

		@Test
		@DisplayName("[findLike()] 좋아요 존재 -> Optional 반환")
		void findLikeExists() {
			// Arrange
			Long userId = 1L;
			Long targetId = 100L;
			ProductLike like = ProductLike.reconstruct(1L, userId, targetId, LocalDateTime.now());
			given(productLikeQueryRepository.findByUserIdAndTargetId(userId, targetId))
				.willReturn(Optional.of(like));

			// Act
			Optional<ProductLike> result = productLikeCommandService.findLike(userId, targetId);

			// Assert
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get().getId()).isEqualTo(1L)
			);
		}

		@Test
		@DisplayName("[findLike()] 좋아요 미존재 -> Optional.empty 반환")
		void findLikeNotExists() {
			// Arrange
			given(productLikeQueryRepository.findByUserIdAndTargetId(1L, 100L))
				.willReturn(Optional.empty());

			// Act
			Optional<ProductLike> result = productLikeCommandService.findLike(1L, 100L);

			// Assert
			assertThat(result).isEmpty();
		}
	}


	@Nested
	@DisplayName("createLike() - 상품 좋아요 생성")
	class CreateLikeTest {

		@Test
		@DisplayName("[createLike()] 유효한 요청 -> 좋아요 생성 및 저장")
		void createLikeSuccess() {
			// Arrange
			Long userId = 1L;
			Long targetId = 100L;
			ProductLike savedLike = ProductLike.reconstruct(1L, userId, targetId, LocalDateTime.now());

			willDoNothing().given(productLikeTargetValidator).validate(targetId);
			given(productLikeCommandRepository.save(any(ProductLike.class))).willReturn(savedLike);

			// Act
			ProductLike result = productLikeCommandService.createLike(userId, targetId);

			// Assert
			assertThat(result.getId()).isEqualTo(1L);
			verify(productLikeCommandRepository).save(any(ProductLike.class));
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
		@DisplayName("[deleteLike()] 좋아요 존재 -> 삭제 성공")
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


	@Nested
	@DisplayName("increaseLikeCount() - 좋아요 수 증가")
	class IncreaseLikeCountTest {

		@Test
		@DisplayName("[increaseLikeCount()] 유효한 상품 ID -> ProductLikeCountSyncer에 위임")
		void increaseLikeCountSuccess() {
			// Arrange
			willDoNothing().given(productLikeCountSyncer).increaseLikeCount(1L);

			// Act
			productLikeCommandService.increaseLikeCount(1L);

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
			productLikeCommandService.decreaseLikeCount(1L);

			// Assert
			verify(productLikeCountSyncer).decreaseLikeCount(1L);
		}

	}

}
