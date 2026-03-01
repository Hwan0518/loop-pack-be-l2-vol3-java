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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("ProductLikeCommandFacade 테스트")
class ProductLikeCommandFacadeTest {

	@Mock
	private ProductLikeCommandService productLikeCommandService;

	private ProductLikeCommandFacade productLikeCommandFacade;


	@BeforeEach
	void setUp() {
		productLikeCommandFacade = new ProductLikeCommandFacade(
			productLikeCommandService
		);
	}


	@Nested
	@DisplayName("createLike() - 상품 좋아요 생성")
	class CreateLikeTest {

		@Test
		@DisplayName("[createLike()] 신규 좋아요 -> OutDto 반환 + 좋아요 수 증가 호출")
		void createLike() {
			// Arrange
			String loginId = "loginId";
			String password = "password";
			Long userId = 1L;
			Long targetId = 100L;
			ProductLike like = ProductLike.reconstruct(1L, userId, targetId, LocalDateTime.now());

			given(productLikeCommandService.authenticate(loginId, password)).willReturn(userId);
			given(productLikeCommandService.findLike(userId, targetId)).willReturn(Optional.empty());
			given(productLikeCommandService.createLike(userId, targetId)).willReturn(like);
			willDoNothing().given(productLikeCommandService).increaseLikeCount(targetId);

			// Act
			ProductLikeOutDto result = productLikeCommandFacade.createLike(loginId, password, targetId);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.userId()).isEqualTo(1L),
				() -> assertThat(result.targetId()).isEqualTo(100L),
				() -> verify(productLikeCommandService).createLike(userId, targetId),
				() -> verify(productLikeCommandService).increaseLikeCount(targetId)
			);
		}

		@Test
		@DisplayName("[createLike()] 기존 좋아요 존재 -> 기존 반환 (멱등). 좋아요 수 증가 미호출")
		void createLikeIdempotent() {
			// Arrange
			String loginId = "loginId";
			String password = "password";
			Long userId = 1L;
			Long targetId = 100L;
			ProductLike existingLike = ProductLike.reconstruct(1L, userId, targetId, LocalDateTime.now());

			given(productLikeCommandService.authenticate(loginId, password)).willReturn(userId);
			given(productLikeCommandService.findLike(userId, targetId)).willReturn(Optional.of(existingLike));

			// Act
			ProductLikeOutDto result = productLikeCommandFacade.createLike(loginId, password, targetId);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> verify(productLikeCommandService, never()).createLike(any(), any()),
				() -> verify(productLikeCommandService, never()).increaseLikeCount(any())
			);
		}
	}


	@Nested
	@DisplayName("deleteLike() - 상품 좋아요 삭제")
	class DeleteLikeTest {

		@Test
		@DisplayName("[deleteLike()] 유효한 요청 -> 서비스 위임 + 좋아요 수 감소 호출")
		void deleteLike() {
			// Arrange
			String loginId = "loginId";
			String password = "password";
			Long userId = 1L;
			Long targetId = 100L;

			given(productLikeCommandService.authenticate(loginId, password)).willReturn(userId);
			willDoNothing().given(productLikeCommandService).deleteLike(userId, targetId);
			willDoNothing().given(productLikeCommandService).decreaseLikeCount(targetId);

			// Act
			productLikeCommandFacade.deleteLike(loginId, password, targetId);

			// Assert
			verify(productLikeCommandService).authenticate(loginId, password);
			verify(productLikeCommandService).deleteLike(userId, targetId);
			verify(productLikeCommandService).decreaseLikeCount(targetId);
		}
	}


	@Nested
	@DisplayName("deleteAllByProductId() - 상품 ID로 좋아요 전체 삭제")
	class DeleteAllByProductIdTest {

		@Test
		@DisplayName("[deleteAllByProductId()] 유효한 상품 ID -> 서비스 deleteAllByTargetId 위임")
		void deleteAllByProductId() {
			// Arrange
			willDoNothing().given(productLikeCommandService).deleteAllByTargetId(100L);

			// Act
			productLikeCommandFacade.deleteAllByProductId(100L);

			// Assert
			verify(productLikeCommandService).deleteAllByTargetId(100L);
		}
	}

}
