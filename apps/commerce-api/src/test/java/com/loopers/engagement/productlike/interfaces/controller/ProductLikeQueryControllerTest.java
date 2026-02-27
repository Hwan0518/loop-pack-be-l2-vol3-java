package com.loopers.engagement.productlike.interfaces.controller;


import com.loopers.engagement.productlike.application.dto.out.ProductLikeOutDto;
import com.loopers.engagement.productlike.application.dto.out.ProductLikePageOutDto;
import com.loopers.engagement.productlike.application.facade.ProductLikeQueryFacade;
import com.loopers.engagement.productlike.interfaces.web.controller.ProductLikeQueryController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(ProductLikeQueryController.class)
@DisplayName("ProductLikeQueryController 테스트")
class ProductLikeQueryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductLikeQueryFacade productLikeQueryFacade;

	private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
	private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
	private static final String LOGIN_ID = "testuser";
	private static final String LOGIN_PW = "password123";


	@Nested
	@DisplayName("GET /api/v1/users/me/product-likes - 내 상품 좋아요 목록")
	class GetMyProductLikesTest {

		@Test
		@DisplayName("[GET /api/v1/users/me/product-likes] 좋아요 2건 -> 200 OK. content 크기 2")
		void getMyLikesSuccess() throws Exception {
			// Arrange
			ProductLikePageOutDto outDto = new ProductLikePageOutDto(
				List.of(
					new ProductLikeOutDto(1L, 1L, 100L, LocalDateTime.now()),
					new ProductLikeOutDto(2L, 1L, 200L, LocalDateTime.now())
				), 0, 20, 2
			);
			given(productLikeQueryFacade.getLikesByUserId(anyString(), anyString(), eq(0), eq(20)))
				.willReturn(outDto);

			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me/product-likes")
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.totalElements").value(2));
		}

		@Test
		@DisplayName("[GET /api/v1/users/me/product-likes] 인증 헤더 누락 -> 401 Unauthorized")
		void getMyLikesUnauthorized() throws Exception {
			mockMvc.perform(get("/api/v1/users/me/product-likes"))
				.andExpect(status().isUnauthorized());
		}
	}


	@Nested
	@DisplayName("GET /api/v1/users/me/product-likes/check - 좋아요 여부 확인")
	class CheckProductLikeTest {

		@Test
		@DisplayName("[GET /api/v1/users/me/product-likes/check] 좋아요 존재 -> 200 OK. liked=true")
		void checkLikedTrue() throws Exception {
			// Arrange
			given(productLikeQueryFacade.isLikedByUser(anyString(), anyString(), eq(100L))).willReturn(true);

			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me/product-likes/check")
					.param("targetId", "100")
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(true));
		}

		@Test
		@DisplayName("[GET /api/v1/users/me/product-likes/check] 좋아요 미존재 -> 200 OK. liked=false")
		void checkLikedFalse() throws Exception {
			// Arrange
			given(productLikeQueryFacade.isLikedByUser(anyString(), anyString(), eq(100L))).willReturn(false);

			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me/product-likes/check")
					.param("targetId", "100")
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(false));
		}

		@Test
		@DisplayName("[GET /api/v1/users/me/product-likes/check] 인증 헤더 누락 -> 401 Unauthorized")
		void checkUnauthorized() throws Exception {
			mockMvc.perform(get("/api/v1/users/me/product-likes/check")
					.param("targetId", "100"))
				.andExpect(status().isUnauthorized());
		}
	}

}
