package com.loopers.cart.cart.interfaces;


import com.loopers.cart.cart.application.dto.out.CartItemOutDto;
import com.loopers.cart.cart.application.dto.out.CartOutDto;
import com.loopers.cart.cart.application.dto.out.CartStatusItemOutDto;
import com.loopers.cart.cart.application.dto.out.CartStatusOutDto;
import com.loopers.cart.cart.application.facade.CartItemQueryFacade;
import com.loopers.cart.cart.interfaces.web.controller.CartItemQueryController;
import com.loopers.support.common.auth.AuthenticationResolver;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(CartItemQueryController.class)
@DisplayName("CartItemQueryController 테스트")
class CartItemQueryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CartItemQueryFacade cartItemQueryFacade;

	@MockitoBean
	private AuthenticationResolver authenticationResolver;

	private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
	private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
	private static final String LOGIN_ID = "testuser";
	private static final String LOGIN_PW = "password123";
	private static final Long USER_ID = 1L;


	@Nested
	@DisplayName("GET /api/v1/cart - 장바구니 조회")
	class GetCartTest {

		@Test
		@DisplayName("[GET /api/v1/cart] 장바구니 항목 존재 -> 200 OK. items, totalCount 포함")
		void getCartSuccess() throws Exception {
			// Arrange
			CartOutDto outDto = new CartOutDto(
				List.of(
					new CartItemOutDto(1L, 1L, 100L, 3L, true, LocalDateTime.now()),
					new CartItemOutDto(2L, 1L, 200L, 2L, false, LocalDateTime.now())
				),
				2
			);

			given(authenticationResolver.resolve(LOGIN_ID, LOGIN_PW)).willReturn(USER_ID);
			given(cartItemQueryFacade.getCart(anyLong())).willReturn(outDto);

			// Act & Assert
			mockMvc.perform(get("/api/v1/cart")
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(2)))
				.andExpect(jsonPath("$.totalCount").value(2))
				.andExpect(jsonPath("$.items[0].productId").value(100L))
				.andExpect(jsonPath("$.items[1].selected").value(false));
		}


		@Test
		@DisplayName("[GET /api/v1/cart] 장바구니 비어있음 -> 200 OK. 빈 목록, totalCount=0")
		void getCartEmpty() throws Exception {
			// Arrange
			CartOutDto outDto = new CartOutDto(List.of(), 0);
			given(authenticationResolver.resolve(LOGIN_ID, LOGIN_PW)).willReturn(USER_ID);
			given(cartItemQueryFacade.getCart(anyLong())).willReturn(outDto);

			// Act & Assert
			mockMvc.perform(get("/api/v1/cart")
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(0)))
				.andExpect(jsonPath("$.totalCount").value(0));
		}


		@Test
		@DisplayName("[GET /api/v1/cart] 인증 헤더 누락 -> 401 Unauthorized")
		void getCartUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/cart"))
				.andExpect(status().isUnauthorized());
		}


		@Test
		@DisplayName("[GET /api/v1/cart] LoginId만 존재, LoginPw 누락 -> 401 Unauthorized")
		void getCartMissingPassword() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/cart")
					.header(LOGIN_ID_HEADER, LOGIN_ID))
				.andExpect(status().isUnauthorized());
		}

	}


	@Nested
	@DisplayName("GET /api/v1/cart/status - 장바구니 상태 조회")
	class GetCartStatusTest {

		@Test
		@DisplayName("[GET /api/v1/cart/status] 3개 항목 중 2개 선택 -> 200 OK. totalCount=3, selectedCount=2")
		void getCartStatusSuccess() throws Exception {
			// Arrange
			CartStatusOutDto outDto = new CartStatusOutDto(3, 2,
				List.of(
					new CartStatusItemOutDto(1L, 100L, 1L, true),
					new CartStatusItemOutDto(2L, 200L, 2L, true),
					new CartStatusItemOutDto(3L, 300L, 3L, false)
				));

			given(authenticationResolver.resolve(LOGIN_ID, LOGIN_PW)).willReturn(USER_ID);
			given(cartItemQueryFacade.getCartStatus(anyLong())).willReturn(outDto);

			// Act & Assert
			mockMvc.perform(get("/api/v1/cart/status")
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCount").value(3))
				.andExpect(jsonPath("$.selectedCount").value(2))
				.andExpect(jsonPath("$.items", hasSize(3)));
		}


		@Test
		@DisplayName("[GET /api/v1/cart/status] 인증 헤더 누락 -> 401 Unauthorized")
		void getCartStatusUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/cart/status"))
				.andExpect(status().isUnauthorized());
		}

	}

}
