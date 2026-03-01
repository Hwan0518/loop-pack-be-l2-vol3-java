package com.loopers.engagement.productlike.interfaces.controller;


import com.loopers.engagement.productlike.application.dto.out.ProductLikeOutDto;
import com.loopers.engagement.productlike.application.facade.ProductLikeCommandFacade;
import com.loopers.engagement.productlike.interfaces.web.controller.ProductLikeCommandController;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(ProductLikeCommandController.class)
@DisplayName("ProductLikeCommandController 테스트")
class ProductLikeCommandControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductLikeCommandFacade productLikeCommandFacade;

	private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
	private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
	private static final String LOGIN_ID = "testuser";
	private static final String LOGIN_PW = "password123";


	@Nested
	@DisplayName("POST /api/v1/products/{id}/likes - 상품 좋아요 생성")
	class CreateProductLikeTest {

		@Test
		@DisplayName("[POST /api/v1/products/{id}/likes] 유효한 요청 -> 200 OK. id, targetId 포함")
		void createSuccess() throws Exception {
			// Arrange
			ProductLikeOutDto outDto = new ProductLikeOutDto(1L, 1L, 100L, LocalDateTime.now());
			given(productLikeCommandFacade.createLike(anyString(), anyString(), eq(100L))).willReturn(outDto);

			// Act & Assert
			mockMvc.perform(post("/api/v1/products/{id}/likes", 100L)
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1L))
				.andExpect(jsonPath("$.targetId").value(100L));
		}

		@Test
		@DisplayName("[POST /api/v1/products/{id}/likes] 인증 헤더 누락 -> 401 Unauthorized")
		void createUnauthorized() throws Exception {
			mockMvc.perform(post("/api/v1/products/{id}/likes", 100L))
				.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("[POST /api/v1/products/{id}/likes] 대상 상품 미존재 -> 404 Not Found. LIKE_TARGET_NOT_FOUND")
		void createTargetNotFound() throws Exception {
			// Arrange
			given(productLikeCommandFacade.createLike(anyString(), anyString(), eq(999L)))
				.willThrow(new CoreException(ErrorType.LIKE_TARGET_NOT_FOUND));

			// Act & Assert
			mockMvc.perform(post("/api/v1/products/{id}/likes", 999L)
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.LIKE_TARGET_NOT_FOUND.getCode()));
		}
	}


	@Nested
	@DisplayName("DELETE /api/v1/products/{id}/likes - 상품 좋아요 삭제")
	class DeleteProductLikeTest {

		@Test
		@DisplayName("[DELETE /api/v1/products/{id}/likes] 유효한 요청 -> 200 OK")
		void deleteSuccess() throws Exception {
			// Arrange
			willDoNothing().given(productLikeCommandFacade).deleteLike(anyString(), anyString(), eq(100L));

			// Act & Assert
			mockMvc.perform(delete("/api/v1/products/{id}/likes", 100L)
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW))
				.andExpect(status().isOk());
		}

		@Test
		@DisplayName("[DELETE /api/v1/products/{id}/likes] 인증 헤더 누락 -> 401 Unauthorized")
		void deleteUnauthorized() throws Exception {
			mockMvc.perform(delete("/api/v1/products/{id}/likes", 100L))
				.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("[DELETE /api/v1/products/{id}/likes] 좋아요 미존재 -> 404 Not Found. LIKE_NOT_FOUND")
		void deleteNotFound() throws Exception {
			// Arrange
			willThrow(new CoreException(ErrorType.LIKE_NOT_FOUND))
				.given(productLikeCommandFacade).deleteLike(anyString(), anyString(), eq(100L));

			// Act & Assert
			mockMvc.perform(delete("/api/v1/products/{id}/likes", 100L)
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.LIKE_NOT_FOUND.getCode()));
		}
	}

}
