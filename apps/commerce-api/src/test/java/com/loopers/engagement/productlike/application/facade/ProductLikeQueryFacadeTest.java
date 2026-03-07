package com.loopers.engagement.productlike.application.facade;


import com.loopers.engagement.productlike.application.dto.out.ProductLikeOutDto;
import com.loopers.engagement.productlike.application.dto.out.ProductLikePageOutDto;
import com.loopers.engagement.productlike.application.service.ProductLikeQueryService;
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
@DisplayName("ProductLikeQueryFacade 테스트")
class ProductLikeQueryFacadeTest {

	@Mock
	private ProductLikeQueryService productLikeQueryService;

	private ProductLikeQueryFacade productLikeQueryFacade;


	@BeforeEach
	void setUp() {
		productLikeQueryFacade = new ProductLikeQueryFacade(productLikeQueryService);
	}


	@Nested
	@DisplayName("getLikesByUserId() - 좋아요 목록 조회")
	class GetLikesByUserIdTest {

		@Test
		@DisplayName("[getLikesByUserId()] 조회 요청 -> 페이지 결과 반환")
		void getLikesByUserId() {
			// Arrange
			Long userId = 1L;
			ProductLikePageOutDto outDto = new ProductLikePageOutDto(
				List.of(new ProductLikeOutDto(1L, userId, 100L, LocalDateTime.now())),
				0, 20, 1
			);
			given(productLikeQueryService.getLikesByUserId(userId, 0, 20)).willReturn(outDto);

			// Act
			ProductLikePageOutDto result = productLikeQueryFacade.getLikesByUserId(userId, 0, 20);

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

			given(productLikeQueryService.isLikedByUser(userId, targetId)).willReturn(true);

			// Act & Assert
			assertThat(productLikeQueryFacade.isLikedByUser(userId, targetId)).isTrue();
		}


		@Test
		@DisplayName("[isLikedByUser()] 좋아요 미존재 -> false")
		void isLikedFalse() {
			// Arrange
			Long userId = 1L;
			Long targetId = 100L;

			given(productLikeQueryService.isLikedByUser(userId, targetId)).willReturn(false);

			// Act & Assert
			assertThat(productLikeQueryFacade.isLikedByUser(userId, targetId)).isFalse();
		}
	}

}
