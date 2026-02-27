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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
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
		@DisplayName("[createLike()] 유효한 요청 -> OutDto 반환. id, userId, targetId 포함")
		void createLike() {
			// Arrange
			BrandLike like = BrandLike.reconstruct(1L, 1L, 100L, LocalDateTime.now());
			given(brandLikeCommandService.authenticate("loginId", "password")).willReturn(1L);
			given(brandLikeCommandService.createLike(1L, 100L)).willReturn(like);

			// Act
			BrandLikeOutDto result = brandLikeCommandFacade.createLike("loginId", "password", 100L);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.userId()).isEqualTo(1L),
				() -> assertThat(result.targetId()).isEqualTo(100L)
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
			given(brandLikeCommandService.authenticate("loginId", "password")).willReturn(1L);
			willDoNothing().given(brandLikeCommandService).deleteLike(1L, 100L);

			// Act
			brandLikeCommandFacade.deleteLike("loginId", "password", 100L);

			// Assert
			verify(brandLikeCommandService).deleteLike(1L, 100L);
		}
	}

}
