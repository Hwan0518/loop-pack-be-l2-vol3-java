package com.loopers.engagement.brandlike.application.facade;


import com.loopers.engagement.brandlike.application.dto.out.BrandLikeOutDto;
import com.loopers.engagement.brandlike.application.dto.out.BrandLikePageOutDto;
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
	private BrandLikeQueryService brandLikeQueryService;

	private BrandLikeQueryFacade brandLikeQueryFacade;


	@BeforeEach
	void setUp() {
		brandLikeQueryFacade = new BrandLikeQueryFacade(brandLikeQueryService);
	}


	@Nested
	@DisplayName("getLikesByUserId() - 좋아요 목록 조회")
	class GetLikesByUserIdTest {

		@Test
		@DisplayName("[getLikesByUserId()] 조회 요청 -> 페이지 결과 반환")
		void getLikesByUserId() {
			// Arrange
			Long userId = 1L;
			BrandLikePageOutDto outDto = new BrandLikePageOutDto(
				List.of(new BrandLikeOutDto(1L, userId, 100L, LocalDateTime.now())),
				0, 20, 1
			);
			given(brandLikeQueryService.getLikesByUserId(userId, 0, 20)).willReturn(outDto);

			// Act
			BrandLikePageOutDto result = brandLikeQueryFacade.getLikesByUserId(userId, 0, 20);

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
			Long userId = 1L;
			Long targetId = 100L;
			given(brandLikeQueryService.isLikedByUser(userId, targetId)).willReturn(true);

			// Act & Assert
			assertThat(brandLikeQueryFacade.isLikedByUser(userId, targetId)).isTrue();
		}


		@Test
		@DisplayName("[isLikedByUser()] 좋아요 미존재 -> false")
		void isLikedFalse() {
			// Arrange
			Long userId = 1L;
			Long targetId = 100L;
			given(brandLikeQueryService.isLikedByUser(userId, targetId)).willReturn(false);

			// Act & Assert
			assertThat(brandLikeQueryFacade.isLikedByUser(userId, targetId)).isFalse();
		}
	}

}
