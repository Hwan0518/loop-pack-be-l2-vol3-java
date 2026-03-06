package com.loopers.engagement.brandlike.application.service;


import com.loopers.engagement.brandlike.application.port.out.client.catalog.BrandLikeTargetValidator;
import com.loopers.engagement.brandlike.application.port.out.client.user.UserAuthenticator;
import com.loopers.engagement.brandlike.domain.model.BrandLike;
import com.loopers.engagement.brandlike.domain.repository.BrandLikeCommandRepository;
import com.loopers.engagement.brandlike.domain.repository.BrandLikeQueryRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
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
@DisplayName("BrandLikeCommandService 테스트")
class BrandLikeCommandServiceTest {

	@Mock
	private BrandLikeCommandRepository brandLikeCommandRepository;
	@Mock
	private BrandLikeQueryRepository brandLikeQueryRepository;
	@Mock
	private BrandLikeTargetValidator brandLikeTargetValidator;
	@Mock
	private UserAuthenticator userAuthenticator;

	private BrandLikeCommandService brandLikeCommandService;


	@BeforeEach
	void setUp() {
		brandLikeCommandService = new BrandLikeCommandService(
			brandLikeCommandRepository,
			brandLikeQueryRepository,
			brandLikeTargetValidator,
			userAuthenticator
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
			BrandLike existingLike = BrandLike.reconstruct(1L, userId, targetId, LocalDateTime.now());
			given(brandLikeQueryRepository.findByUserIdAndTargetId(userId, targetId))
				.willReturn(Optional.of(existingLike));

			// Act
			Optional<BrandLike> result = brandLikeCommandService.findLike(userId, targetId);

			// Assert
			assertThat(result).isPresent();
			assertThat(result.get().getId()).isEqualTo(1L);
		}

		@Test
		@DisplayName("[findLike()] 좋아요 미존재 -> Optional.empty 반환")
		void findLikeNotExists() {
			// Arrange
			given(brandLikeQueryRepository.findByUserIdAndTargetId(1L, 100L))
				.willReturn(Optional.empty());

			// Act
			Optional<BrandLike> result = brandLikeCommandService.findLike(1L, 100L);

			// Assert
			assertThat(result).isEmpty();
		}
	}


	@Nested
	@DisplayName("createLike() - 브랜드 좋아요 생성")
	class CreateLikeTest {

		@Test
		@DisplayName("[createLike()] 신규 좋아요 -> 저장 후 반환")
		void createLikeSuccess() {
			// Arrange
			Long userId = 1L;
			Long targetId = 100L;
			BrandLike savedLike = BrandLike.reconstruct(1L, userId, targetId, LocalDateTime.now());

			willDoNothing().given(brandLikeTargetValidator).validate(targetId);
			given(brandLikeCommandRepository.save(any(BrandLike.class))).willReturn(savedLike);

			// Act
			BrandLike result = brandLikeCommandService.createLike(userId, targetId);

			// Assert
			assertThat(result.getId()).isEqualTo(1L);
			verify(brandLikeCommandRepository).save(any(BrandLike.class));
		}

		@Test
		@DisplayName("[createLike()] 대상 브랜드 미존재 -> LIKE_TARGET_NOT_FOUND 예외. Provider 예외(BRAND_NOT_FOUND)를 Service에서 매핑")
		void createLikeTargetNotFound() {
			// Arrange — Port는 Provider 원본 예외(BRAND_NOT_FOUND)를 전파
			willThrow(new CoreException(ErrorType.BRAND_NOT_FOUND))
				.given(brandLikeTargetValidator).validate(999L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> brandLikeCommandService.createLike(1L, 999L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.LIKE_TARGET_NOT_FOUND);
		}
	}


	@Nested
	@DisplayName("deleteLike() - 브랜드 좋아요 삭제")
	class DeleteLikeTest {

		@Test
		@DisplayName("[deleteLike()] 좋아요 존재 -> 삭제 성공")
		void deleteLikeSuccess() {
			// Arrange
			Long userId = 1L;
			Long targetId = 100L;
			BrandLike existingLike = BrandLike.reconstruct(1L, userId, targetId, LocalDateTime.now());

			given(brandLikeQueryRepository.findByUserIdAndTargetId(userId, targetId))
				.willReturn(Optional.of(existingLike));
			willDoNothing().given(brandLikeCommandRepository).delete(existingLike);

			// Act
			brandLikeCommandService.deleteLike(userId, targetId);

			// Assert
			verify(brandLikeCommandRepository).delete(existingLike);
		}

		@Test
		@DisplayName("[deleteLike()] 좋아요 미존재 -> LIKE_NOT_FOUND 예외")
		void deleteLikeNotFound() {
			// Arrange
			given(brandLikeQueryRepository.findByUserIdAndTargetId(1L, 100L))
				.willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> brandLikeCommandService.deleteLike(1L, 100L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.LIKE_NOT_FOUND);
		}
	}


	@Nested
	@DisplayName("deleteAllByTargetId() - 브랜드 ID로 전체 삭제")
	class DeleteAllByTargetIdTest {

		@Test
		@DisplayName("[deleteAllByTargetId()] 삭제 호출 -> repository 위임")
		void deleteAllByTargetId() {
			// Arrange
			willDoNothing().given(brandLikeCommandRepository).deleteAllByTargetId(100L);

			// Act
			brandLikeCommandService.deleteAllByTargetId(100L);

			// Assert
			verify(brandLikeCommandRepository).deleteAllByTargetId(100L);
		}
	}

}
