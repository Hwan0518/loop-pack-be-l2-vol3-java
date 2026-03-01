package com.loopers.engagement.productlike.application.service;


import com.loopers.engagement.productlike.application.dto.out.ProductLikePageOutDto;
import com.loopers.engagement.productlike.domain.model.ProductLike;
import com.loopers.engagement.productlike.domain.repository.ProductLikeQueryRepository;
import com.loopers.engagement.productlike.domain.repository.vo.PageCriteria;
import com.loopers.engagement.productlike.domain.repository.vo.PageResult;
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
@DisplayName("ProductLikeQueryService 테스트")
class ProductLikeQueryServiceTest {

	@Mock
	private ProductLikeQueryRepository productLikeQueryRepository;

	private ProductLikeQueryService productLikeQueryService;


	@BeforeEach
	void setUp() {
		productLikeQueryService = new ProductLikeQueryService(productLikeQueryRepository);
	}


	@Nested
	@DisplayName("getLikesByUserId() - 좋아요 목록 조회")
	class GetLikesByUserIdTest {

		@Test
		@DisplayName("[getLikesByUserId()] 좋아요 2건 존재 -> 페이지 결과 반환. content 크기 2")
		void getLikesByUserId() {
			// Arrange
			ProductLike like1 = ProductLike.reconstruct(1L, 1L, 100L, LocalDateTime.now());
			ProductLike like2 = ProductLike.reconstruct(2L, 1L, 200L, LocalDateTime.now());
			PageResult<ProductLike> pageResult = new PageResult<>(List.of(like1, like2), 0, 20, 2);

			given(productLikeQueryRepository.findByUserId(1L, new PageCriteria(0, 20)))
				.willReturn(pageResult);

			// Act
			ProductLikePageOutDto result = productLikeQueryService.getLikesByUserId(1L, 0, 20);

			// Assert
			assertThat(result.content()).hasSize(2);
			assertThat(result.totalElements()).isEqualTo(2);
		}
	}


	@Nested
	@DisplayName("isLikedByUser() - 좋아요 여부 확인")
	class IsLikedByUserTest {

		@Test
		@DisplayName("[isLikedByUser()] 좋아요 존재 -> true 반환")
		void isLikedTrue() {
			// Arrange
			given(productLikeQueryRepository.existsByUserIdAndTargetId(1L, 100L)).willReturn(true);

			// Act & Assert
			assertThat(productLikeQueryService.isLikedByUser(1L, 100L)).isTrue();
		}

		@Test
		@DisplayName("[isLikedByUser()] 좋아요 미존재 -> false 반환")
		void isLikedFalse() {
			// Arrange
			given(productLikeQueryRepository.existsByUserIdAndTargetId(1L, 100L)).willReturn(false);

			// Act & Assert
			assertThat(productLikeQueryService.isLikedByUser(1L, 100L)).isFalse();
		}
	}

}
