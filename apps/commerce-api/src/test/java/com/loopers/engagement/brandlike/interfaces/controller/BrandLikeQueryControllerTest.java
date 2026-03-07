package com.loopers.engagement.brandlike.interfaces.controller;


import com.loopers.engagement.brandlike.application.dto.out.BrandLikeOutDto;
import com.loopers.engagement.brandlike.application.dto.out.BrandLikePageOutDto;
import com.loopers.engagement.brandlike.application.facade.BrandLikeQueryFacade;
import com.loopers.engagement.brandlike.interfaces.web.controller.BrandLikeQueryController;
import com.loopers.support.common.auth.AuthenticationResolver;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
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


@WebMvcTest(BrandLikeQueryController.class)
@DisplayName("BrandLikeQueryController 테스트")
class BrandLikeQueryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BrandLikeQueryFacade brandLikeQueryFacade;

	@MockitoBean
	private AuthenticationResolver authenticationResolver;

	private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
	private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
	private static final String LOGIN_ID = "testuser";
	private static final String LOGIN_PW = "password123";
	private static final Long USER_ID = 1L;


	@Nested
	@DisplayName("GET /api/v1/users/me/brand-likes - 내 브랜드 좋아요 목록")
	class GetMyBrandLikesTest {

		@Test
		@DisplayName("[GET /api/v1/users/me/brand-likes] 좋아요 2건 -> 200 OK. content 크기 2")
		void getMyLikesSuccess() throws Exception {
			// Arrange
			given(authenticationResolver.resolve(LOGIN_ID, LOGIN_PW)).willReturn(USER_ID);
			BrandLikePageOutDto outDto = new BrandLikePageOutDto(
				List.of(
					new BrandLikeOutDto(1L, 1L, 100L, LocalDateTime.now()),
					new BrandLikeOutDto(2L, 1L, 200L, LocalDateTime.now())
				), 0, 20, 2
			);
			given(brandLikeQueryFacade.getLikesByUserId(anyLong(), eq(0), eq(20)))
				.willReturn(outDto);

			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me/brand-likes")
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.totalElements").value(2));
		}

		@Test
		@DisplayName("[GET /api/v1/users/me/brand-likes] 인증 헤더 누락 -> 401 Unauthorized")
		void getMyLikesUnauthorized() throws Exception {
			// Arrange
			given(authenticationResolver.resolve(isNull(), isNull()))
				.willThrow(new CoreException(ErrorType.AUTHENTICATION_FAILED));

			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me/brand-likes"))
				.andExpect(status().isUnauthorized());
		}
	}


	@Nested
	@DisplayName("GET /api/v1/users/me/brand-likes/check - 좋아요 여부 확인")
	class CheckBrandLikeTest {

		@Test
		@DisplayName("[GET /api/v1/users/me/brand-likes/check] 좋아요 존재 -> 200 OK. liked=true")
		void checkLikedTrue() throws Exception {
			// Arrange
			given(authenticationResolver.resolve(LOGIN_ID, LOGIN_PW)).willReturn(USER_ID);
			given(brandLikeQueryFacade.isLikedByUser(anyLong(), eq(100L))).willReturn(true);

			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me/brand-likes/check")
					.param("targetId", "100")
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(true));
		}

		@Test
		@DisplayName("[GET /api/v1/users/me/brand-likes/check] 좋아요 미존재 -> 200 OK. liked=false")
		void checkLikedFalse() throws Exception {
			// Arrange
			given(authenticationResolver.resolve(LOGIN_ID, LOGIN_PW)).willReturn(USER_ID);
			given(brandLikeQueryFacade.isLikedByUser(anyLong(), eq(100L))).willReturn(false);

			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me/brand-likes/check")
					.param("targetId", "100")
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(false));
		}

		@Test
		@DisplayName("[GET /api/v1/users/me/brand-likes/check] 인증 헤더 누락 -> 401 Unauthorized")
		void checkUnauthorized() throws Exception {
			// Arrange
			given(authenticationResolver.resolve(isNull(), isNull()))
				.willThrow(new CoreException(ErrorType.AUTHENTICATION_FAILED));

			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me/brand-likes/check")
					.param("targetId", "100"))
				.andExpect(status().isUnauthorized());
		}
	}

}
