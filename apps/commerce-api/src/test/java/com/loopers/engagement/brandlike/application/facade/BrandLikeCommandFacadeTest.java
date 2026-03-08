package com.loopers.engagement.brandlike.application.facade;


import com.loopers.engagement.brandlike.application.dto.out.BrandLikeOutDto;
import com.loopers.engagement.brandlike.application.service.BrandLikeCommandService;
import com.loopers.engagement.brandlike.domain.model.BrandLike;
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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("BrandLikeCommandFacade 테스트")
class BrandLikeCommandFacadeTest {

	@Mock
	private BrandLikeCommandService brandLikeCommandService;

	private BrandLikeCommandFacade brandLikeCommandFacade;


	@BeforeEach
	void setUp() {
		brandLikeCommandFacade = new BrandLikeCommandFacade(brandLikeCommandService);
	}


	@Nested
	@DisplayName("createLike() - 브랜드 좋아요 생성")
	class CreateLikeTest {

		@Test
		@DisplayName("[createLike()] 신규 좋아요 -> OutDto 반환. id, userId, targetId 포함")
		void createLike() {
			// Arrange
			Long userId = 1L;
			Long targetId = 100L;
			BrandLike like = BrandLike.reconstruct(1L, userId, targetId, LocalDateTime.now());
			given(brandLikeCommandService.findLike(userId, targetId)).willReturn(Optional.empty());
			given(brandLikeCommandService.createLike(userId, targetId)).willReturn(like);

			// Act
			BrandLikeOutDto result = brandLikeCommandFacade.createLike(userId, targetId);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.userId()).isEqualTo(userId),
				() -> assertThat(result.targetId()).isEqualTo(targetId),
				() -> verify(brandLikeCommandService).createLike(userId, targetId)
			);
		}

		@Test
		@DisplayName("[createLike()] 기존 좋아요 존재 -> 기존 반환 (멱등). 좋아요 생성 미호출")
		void createLikeIdempotent() {
			// Arrange
			Long userId = 1L;
			Long targetId = 100L;
			BrandLike existingLike = BrandLike.reconstruct(1L, userId, targetId, LocalDateTime.now());
			given(brandLikeCommandService.findLike(userId, targetId)).willReturn(Optional.of(existingLike));

			// Act
			BrandLikeOutDto result = brandLikeCommandFacade.createLike(userId, targetId);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> verify(brandLikeCommandService, never()).createLike(any(), any())
			);
		}

	}


	@Nested
	@DisplayName("deleteLike() - 브랜드 좋아요 삭제")
	class DeleteLikeTest {

		@Test
		@DisplayName("[deleteLike()] 유효한 요청 -> 서비스 위임")
		void deleteLike() {
			// Arrange
			Long userId = 1L;
			Long targetId = 100L;
			willDoNothing().given(brandLikeCommandService).deleteLike(userId, targetId);

			// Act
			brandLikeCommandFacade.deleteLike(userId, targetId);

			// Assert
			verify(brandLikeCommandService).deleteLike(userId, targetId);
		}
	}


	@Nested
	@DisplayName("deleteAllByBrandId() - 브랜드 ID로 좋아요 전체 삭제")
	class DeleteAllByBrandIdTest {

		@Test
		@DisplayName("[deleteAllByBrandId()] 유효한 브랜드 ID -> 서비스 deleteAllByTargetId 위임")
		void deleteAllByBrandId() {
			// Arrange
			willDoNothing().given(brandLikeCommandService).deleteAllByTargetId(100L);

			// Act
			brandLikeCommandFacade.deleteAllByBrandId(100L);

			// Assert
			verify(brandLikeCommandService).deleteAllByTargetId(100L);
		}
	}

}
