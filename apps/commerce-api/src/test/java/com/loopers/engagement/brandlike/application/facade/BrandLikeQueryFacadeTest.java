package com.loopers.engagement.brandlike.application.facade;


import com.loopers.engagement.brandlike.application.dto.out.BrandLikeOutDto;
import com.loopers.engagement.brandlike.application.dto.out.BrandLikePageOutDto;
import com.loopers.engagement.brandlike.application.service.BrandLikeCommandService;
import com.loopers.engagement.brandlike.application.service.BrandLikeQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
@DisplayName("BrandLikeQueryFacade 테스트")
class BrandLikeQueryFacadeTest {

	@Mock
	private BrandLikeCommandService brandLikeCommandService;
	@Mock
	private BrandLikeQueryService brandLikeQueryService;

	private BrandLikeQueryFacade brandLikeQueryFacade;


	@BeforeEach
	void setUp() {
		brandLikeQueryFacade = new BrandLikeQueryFacade(brandLikeCommandService, brandLikeQueryService);
	}


	@Nested
	@DisplayName("getLikesByUserId() - 좋아요 목록 조회")
	class GetLikesByUserIdTest {

		@Test
		@DisplayName("[getLikesByUserId()] 조회 요청 -> 페이지 결과 반환")
		void getLikesByUserId() {
			// Arrange
			BrandLikePageOutDto outDto = new BrandLikePageOutDto(
				List.of(new BrandLikeOutDto(1L, 1L, 100L, LocalDateTime.now())),
				0, 20, 1
			);
			given(brandLikeCommandService.authenticate("loginId", "password")).willReturn(1L);
			given(brandLikeQueryService.getLikesByUserId(1L, 0, 20)).willReturn(outDto);

			// Act
			BrandLikePageOutDto result = brandLikeQueryFacade.getLikesByUserId("loginId", "password", 0, 20);

			// Assert
			assertThat(result.content()).hasSize(1);
		}
	}


	@Nested
	@DisplayName("isLikedByUser() - 좋아요 여부 확인")
	class IsLikedByUserTest {

		@Test
		@DisplayName("[isLikedByUser()] 좋아요 존재 -> true")
		void isLikedTrue() {
			// Arrange
			given(brandLikeCommandService.authenticate("loginId", "password")).willReturn(1L);
			given(brandLikeQueryService.isLikedByUser(1L, 100L)).willReturn(true);

			// Act & Assert
			assertThat(brandLikeQueryFacade.isLikedByUser("loginId", "password", 100L)).isTrue();
		}


		@Test
		@DisplayName("[isLikedByUser()] 좋아요 미존재 -> false")
		void isLikedFalse() {
			// Arrange
			given(brandLikeCommandService.authenticate("loginId", "password")).willReturn(1L);
			given(brandLikeQueryService.isLikedByUser(1L, 100L)).willReturn(false);

			// Act & Assert
			assertThat(brandLikeQueryFacade.isLikedByUser("loginId", "password", 100L)).isFalse();
		}
	}

}
