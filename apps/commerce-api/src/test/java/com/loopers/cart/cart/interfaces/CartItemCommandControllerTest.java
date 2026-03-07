package com.loopers.cart.cart.interfaces;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.cart.cart.application.dto.out.CartItemOutDto;
import com.loopers.cart.cart.application.dto.out.CartItemSelectionOutDto;
import com.loopers.cart.cart.application.facade.CartItemCommandFacade;
import com.loopers.cart.cart.interfaces.web.controller.CartItemCommandController;
import com.loopers.cart.cart.interfaces.web.request.CartItemAddRequest;
import com.loopers.cart.cart.interfaces.web.request.CartItemSelectionRequest;
import com.loopers.cart.cart.interfaces.web.request.CartItemUpdateQuantityRequest;
import com.loopers.support.common.auth.AuthenticationResolver;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(CartItemCommandController.class)
@DisplayName("CartItemCommandController 테스트")
class CartItemCommandControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private CartItemCommandFacade cartItemCommandFacade;

	@MockitoBean
	private AuthenticationResolver authenticationResolver;

	private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
	private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
	private static final String LOGIN_ID = "testuser";
	private static final String LOGIN_PW = "password123";
	private static final Long USER_ID = 1L;


	@Nested
	@DisplayName("POST /api/v1/cart/items - 장바구니 항목 추가")
	class AddItemTest {

		@Test
		@DisplayName("[POST /api/v1/cart/items] 유효한 요청 -> 201 Created. id, productId, quantity, selected 포함")
		void addItemSuccess() throws Exception {
			// Arrange
			CartItemAddRequest request = new CartItemAddRequest(100L, 3L);
			CartItemOutDto outDto = new CartItemOutDto(1L, 1L, 100L, 3L, true, LocalDateTime.now());

			given(authenticationResolver.resolve(LOGIN_ID, LOGIN_PW)).willReturn(USER_ID);
			given(cartItemCommandFacade.addItem(anyLong(), any())).willReturn(outDto);

			// Act & Assert
			mockMvc.perform(post("/api/v1/cart/items")
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1L))
				.andExpect(jsonPath("$.productId").value(100L))
				.andExpect(jsonPath("$.quantity").value(3L))
				.andExpect(jsonPath("$.selected").value(true));
		}


		@Test
		@DisplayName("[POST /api/v1/cart/items] 인증 헤더 누락 -> 401 Unauthorized")
		void addItemUnauthorized() throws Exception {
			// Arrange
			CartItemAddRequest request = new CartItemAddRequest(100L, 3L);

			// Act & Assert
			mockMvc.perform(post("/api/v1/cart/items")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized());
		}


		@Test
		@DisplayName("[POST /api/v1/cart/items] productId 누락 -> 400 Bad Request")
		void addItemMissingProductId() throws Exception {
			// Arrange
			String requestJson = """
				{"quantity": 3}
				""";

			// Act & Assert
			mockMvc.perform(post("/api/v1/cart/items")
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW)
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[POST /api/v1/cart/items] quantity 0 -> 400 Bad Request")
		void addItemZeroQuantity() throws Exception {
			// Arrange
			CartItemAddRequest request = new CartItemAddRequest(100L, 0L);

			// Act & Assert
			mockMvc.perform(post("/api/v1/cart/items")
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[POST /api/v1/cart/items] 존재하지 않는 상품 -> 404 Not Found. CART_PRODUCT_NOT_FOUND")
		void addItemProductNotFound() throws Exception {
			// Arrange
			CartItemAddRequest request = new CartItemAddRequest(999L, 3L);
			given(authenticationResolver.resolve(LOGIN_ID, LOGIN_PW)).willReturn(USER_ID);
			given(cartItemCommandFacade.addItem(anyLong(), any()))
				.willThrow(new CoreException(ErrorType.CART_PRODUCT_NOT_FOUND));

			// Act & Assert
			mockMvc.perform(post("/api/v1/cart/items")
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.CART_PRODUCT_NOT_FOUND.getCode()));
		}

	}


	@Nested
	@DisplayName("PUT /api/v1/cart/items/{id} - 장바구니 항목 수량 변경")
	class UpdateQuantityTest {

		@Test
		@DisplayName("[PUT /api/v1/cart/items/{id}] 유효한 요청 -> 200 OK. 변경된 quantity 반환")
		void updateQuantitySuccess() throws Exception {
			// Arrange
			CartItemUpdateQuantityRequest request = new CartItemUpdateQuantityRequest(10L);
			CartItemOutDto outDto = new CartItemOutDto(1L, 1L, 100L, 10L, true, LocalDateTime.now());

			given(authenticationResolver.resolve(LOGIN_ID, LOGIN_PW)).willReturn(USER_ID);
			given(cartItemCommandFacade.updateQuantity(anyLong(), eq(1L), any())).willReturn(outDto);

			// Act & Assert
			mockMvc.perform(put("/api/v1/cart/items/{id}", 1L)
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.quantity").value(10L));
		}


		@Test
		@DisplayName("[PUT /api/v1/cart/items/{id}] 인증 헤더 누락 -> 401 Unauthorized")
		void updateQuantityUnauthorized() throws Exception {
			// Arrange
			CartItemUpdateQuantityRequest request = new CartItemUpdateQuantityRequest(10L);

			// Act & Assert
			mockMvc.perform(put("/api/v1/cart/items/{id}", 1L)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized());
		}


		@Test
		@DisplayName("[PUT /api/v1/cart/items/{id}] 존재하지 않는 항목 -> 404 Not Found. CART_ITEM_NOT_FOUND")
		void updateQuantityNotFound() throws Exception {
			// Arrange
			CartItemUpdateQuantityRequest request = new CartItemUpdateQuantityRequest(10L);
			given(authenticationResolver.resolve(LOGIN_ID, LOGIN_PW)).willReturn(USER_ID);
			given(cartItemCommandFacade.updateQuantity(anyLong(), eq(999L), any()))
				.willThrow(new CoreException(ErrorType.CART_ITEM_NOT_FOUND));

			// Act & Assert
			mockMvc.perform(put("/api/v1/cart/items/{id}", 999L)
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.CART_ITEM_NOT_FOUND.getCode()));
		}

	}


	@Nested
	@DisplayName("DELETE /api/v1/cart/items/{id} - 장바구니 항목 삭제")
	class DeleteItemTest {

		@Test
		@DisplayName("[DELETE /api/v1/cart/items/{id}] 유효한 요청 -> 204 No Content")
		void deleteItemSuccess() throws Exception {
			// Arrange
			given(authenticationResolver.resolve(LOGIN_ID, LOGIN_PW)).willReturn(USER_ID);
			willDoNothing().given(cartItemCommandFacade).deleteItem(anyLong(), eq(1L));

			// Act & Assert
			mockMvc.perform(delete("/api/v1/cart/items/{id}", 1L)
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW))
				.andExpect(status().isNoContent());
		}


		@Test
		@DisplayName("[DELETE /api/v1/cart/items/{id}] 인증 헤더 누락 -> 401 Unauthorized")
		void deleteItemUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(delete("/api/v1/cart/items/{id}", 1L))
				.andExpect(status().isUnauthorized());
		}


		@Test
		@DisplayName("[DELETE /api/v1/cart/items/{id}] 다른 사용자의 항목 -> 404 Not Found. CART_ITEM_NOT_FOUND")
		void deleteItemOwnershipFail() throws Exception {
			// Arrange
			given(authenticationResolver.resolve(LOGIN_ID, LOGIN_PW)).willReturn(USER_ID);
			willThrow(new CoreException(ErrorType.CART_ITEM_NOT_FOUND))
				.given(cartItemCommandFacade).deleteItem(anyLong(), eq(1L));

			// Act & Assert
			mockMvc.perform(delete("/api/v1/cart/items/{id}", 1L)
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.CART_ITEM_NOT_FOUND.getCode()));
		}

	}


	@Nested
	@DisplayName("PUT /api/v1/cart/items/selection - 장바구니 선택 상태 변경")
	class UpdateSelectionTest {

		@Test
		@DisplayName("[PUT /api/v1/cart/items/selection] 유효한 요청 -> 200 OK. selectedIds, deselectedIds 반환")
		void updateSelectionSuccess() throws Exception {
			// Arrange
			CartItemSelectionRequest request = new CartItemSelectionRequest(List.of(1L, 3L));
			CartItemSelectionOutDto outDto = new CartItemSelectionOutDto(List.of(1L, 3L), List.of(2L));

			given(authenticationResolver.resolve(LOGIN_ID, LOGIN_PW)).willReturn(USER_ID);
			given(cartItemCommandFacade.updateSelection(anyLong(), any())).willReturn(outDto);

			// Act & Assert
			mockMvc.perform(put("/api/v1/cart/items/selection")
					.header(LOGIN_ID_HEADER, LOGIN_ID)
					.header(LOGIN_PW_HEADER, LOGIN_PW)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.selectedIds[0]").value(1L))
				.andExpect(jsonPath("$.selectedIds[1]").value(3L))
				.andExpect(jsonPath("$.deselectedIds[0]").value(2L));
		}


		@Test
		@DisplayName("[PUT /api/v1/cart/items/selection] 인증 헤더 누락 -> 401 Unauthorized")
		void updateSelectionUnauthorized() throws Exception {
			// Arrange
			CartItemSelectionRequest request = new CartItemSelectionRequest(List.of(1L));

			// Act & Assert
			mockMvc.perform(put("/api/v1/cart/items/selection")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized());
		}

	}

}
