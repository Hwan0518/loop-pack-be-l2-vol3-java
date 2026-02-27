package com.loopers.engagement.productlike.application.facade;


import com.loopers.engagement.productlike.application.dto.out.ProductLikeOutDto;
import com.loopers.engagement.productlike.application.service.ProductLikeCommandService;
import com.loopers.engagement.productlike.domain.model.ProductLike;
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
@DisplayName("ProductLikeCommandFacade 테스트")
class ProductLikeCommandFacadeTest {

	@Mock
	private ProductLikeCommandService productLikeCommandService;

	private ProductLikeCommandFacade productLikeCommandFacade;


	@BeforeEach
	void setUp() {
		productLikeCommandFacade = new ProductLikeCommandFacade(productLikeCommandService);
	}


	@Nested
	@DisplayName("createLike() - 상품 좋아요 생성")
	class CreateLikeTest {

		@Test
		@DisplayName("[createLike()] 유효한 요청 -> OutDto 반환. id, userId, targetId 포함")
		void createLike() {
			// Arrange
			String loginId = "loginId";
			String password = "password";
			Long userId = 1L;
			Long targetId = 100L;
			ProductLike like = ProductLike.reconstruct(1L, userId, targetId, LocalDateTime.now());

			given(productLikeCommandService.authenticate(loginId, password)).willReturn(userId);
			given(productLikeCommandService.createLike(userId, targetId)).willReturn(like);

			// Act
			ProductLikeOutDto result = productLikeCommandFacade.createLike(loginId, password, targetId);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.userId()).isEqualTo(1L),
				() -> assertThat(result.targetId()).isEqualTo(100L)
			);
		}
	}


	@Nested
	@DisplayName("deleteLike() - 상품 좋아요 삭제")
	class DeleteLikeTest {

		@Test
		@DisplayName("[deleteLike()] 유효한 요청 -> 서비스 위임")
		void deleteLike() {
			// Arrange
			String loginId = "loginId";
			String password = "password";
			Long userId = 1L;
			Long targetId = 100L;

			given(productLikeCommandService.authenticate(loginId, password)).willReturn(userId);
			willDoNothing().given(productLikeCommandService).deleteLike(userId, targetId);

			// Act
			productLikeCommandFacade.deleteLike(loginId, password, targetId);

			// Assert
			verify(productLikeCommandService).authenticate(loginId, password);
			verify(productLikeCommandService).deleteLike(userId, targetId);
		}
	}

}
