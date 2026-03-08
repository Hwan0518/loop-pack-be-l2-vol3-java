package com.loopers.cart.cart.interfaces;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.cart.cart.support.CartTestPortConfig;
import com.loopers.support.common.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.user.user.interfaces.web.request.UserSignUpRequest;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class, CartTestPortConfig.class})
@DisplayName("CartItemController E2E 테스트")
class CartItemControllerE2ETest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	private static final String LOGIN_ID = "testuser01";
	private static final String PASSWORD = "Test1234!";


	@BeforeEach
	void setUp() throws Exception {
		signUpUser(LOGIN_ID, PASSWORD, "테스트유저", LocalDate.of(1990, 1, 15), "test@example.com");
	}


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Nested
	@DisplayName("POST /api/v1/cart/items - 장바구니 항목 추가")
	class AddItemTest {

		@Test
		@DisplayName("[POST /api/v1/cart/items] 유효한 요청 -> 201 Created. 응답: id, productId, quantity, selected 포함")
		void addItemSuccess() throws Exception {
			// Act & Assert
			mockMvc.perform(post("/api/v1/cart/items")
					.header("X-Loopers-LoginId", LOGIN_ID)
					.header("X-Loopers-LoginPw", PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
						"productId", 1L,
						"quantity", 2L
					))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.productId").value(1))
				.andExpect(jsonPath("$.quantity").value(2))
				.andExpect(jsonPath("$.selected").value(true));
		}


		@Test
		@DisplayName("[POST /api/v1/cart/items] 인증 헤더 누락 -> 401 Unauthorized")
		void addItemFailWhenNoAuth() throws Exception {
			// Act & Assert
			mockMvc.perform(post("/api/v1/cart/items")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
						"productId", 1L,
						"quantity", 2L
					))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}


		@Test
		@DisplayName("[POST /api/v1/cart/items] 수량 0 이하 -> 400 Bad Request")
		void addItemFailWhenInvalidQuantity() throws Exception {
			// Act & Assert
			mockMvc.perform(post("/api/v1/cart/items")
					.header("X-Loopers-LoginId", LOGIN_ID)
					.header("X-Loopers-LoginPw", PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
						"productId", 1L,
						"quantity", 0L
					))))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[POST /api/v1/cart/items] productId 누락 -> 400 Bad Request")
		void addItemFailWhenNoProductId() throws Exception {
			// Act & Assert
			mockMvc.perform(post("/api/v1/cart/items")
					.header("X-Loopers-LoginId", LOGIN_ID)
					.header("X-Loopers-LoginPw", PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
						"quantity", 2L
					))))
				.andExpect(status().isBadRequest());
		}
	}


	@Nested
	@DisplayName("PUT /api/v1/cart/items/{id} - 장바구니 항목 수량 변경")
	class UpdateQuantityTest {

		@Test
		@DisplayName("[PUT /api/v1/cart/items/{id}] 유효한 수량 변경 -> 200 OK. 변경된 수량 반환")
		void updateQuantitySuccess() throws Exception {
			// Arrange - 항목 추가
			Long cartItemId = addCartItem(1L, 2L);

			// Act & Assert
			mockMvc.perform(put("/api/v1/cart/items/" + cartItemId)
					.header("X-Loopers-LoginId", LOGIN_ID)
					.header("X-Loopers-LoginPw", PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
						"quantity", 5L
					))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(cartItemId))
				.andExpect(jsonPath("$.quantity").value(5));
		}


		@Test
		@DisplayName("[PUT /api/v1/cart/items/{id}] 존재하지 않는 항목 -> 404 Not Found")
		void updateQuantityFailWhenNotFound() throws Exception {
			// Act & Assert
			mockMvc.perform(put("/api/v1/cart/items/99999")
					.header("X-Loopers-LoginId", LOGIN_ID)
					.header("X-Loopers-LoginPw", PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
						"quantity", 5L
					))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.CART_ITEM_NOT_FOUND.getCode()));
		}
	}


	@Nested
	@DisplayName("DELETE /api/v1/cart/items/{id} - 장바구니 항목 삭제")
	class DeleteItemTest {

		@Test
		@DisplayName("[DELETE /api/v1/cart/items/{id}] 유효한 삭제 -> 204 No Content")
		void deleteItemSuccess() throws Exception {
			// Arrange - 항목 추가
			Long cartItemId = addCartItem(1L, 2L);

			// Act & Assert
			mockMvc.perform(delete("/api/v1/cart/items/" + cartItemId)
					.header("X-Loopers-LoginId", LOGIN_ID)
					.header("X-Loopers-LoginPw", PASSWORD))
				.andExpect(status().isNoContent());
		}


		@Test
		@DisplayName("[DELETE /api/v1/cart/items/{id}] 존재하지 않는 항목 -> 404 Not Found")
		void deleteItemFailWhenNotFound() throws Exception {
			// Act & Assert
			mockMvc.perform(delete("/api/v1/cart/items/99999")
					.header("X-Loopers-LoginId", LOGIN_ID)
					.header("X-Loopers-LoginPw", PASSWORD))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.CART_ITEM_NOT_FOUND.getCode()));
		}
	}


	@Nested
	@DisplayName("PUT /api/v1/cart/items/selection - 선택 상태 변경")
	class UpdateSelectionTest {

		@Test
		@DisplayName("[PUT /api/v1/cart/items/selection] 선택 상태 변경 -> 200 OK. selectedIds, deselectedIds 반환")
		void updateSelectionSuccess() throws Exception {
			// Arrange - 항목 2개 추가
			Long item1 = addCartItem(1L, 1L);
			Long item2 = addCartItem(2L, 1L);

			// Act & Assert - item1만 선택
			mockMvc.perform(put("/api/v1/cart/items/selection")
					.header("X-Loopers-LoginId", LOGIN_ID)
					.header("X-Loopers-LoginPw", PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
						"selectedCartItemIds", List.of(item1)
					))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.selectedIds").isArray())
				.andExpect(jsonPath("$.deselectedIds").isArray());
		}


		@Test
		@DisplayName("[PUT /api/v1/cart/items/selection] 인증 헤더 누락 -> 401 Unauthorized")
		void updateSelectionFailWhenNoAuth() throws Exception {
			// Act & Assert
			mockMvc.perform(put("/api/v1/cart/items/selection")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
						"selectedCartItemIds", List.of(1L)
					))))
				.andExpect(status().isUnauthorized());
		}
	}


	@Nested
	@DisplayName("GET /api/v1/cart - 장바구니 조회")
	class GetCartTest {

		@Test
		@DisplayName("[GET /api/v1/cart] 장바구니 조회 -> 200 OK. items 목록 반환")
		void getCartSuccess() throws Exception {
			// Arrange - 항목 2개 추가
			addCartItem(1L, 2L);
			addCartItem(2L, 3L);

			// Act & Assert
			mockMvc.perform(get("/api/v1/cart")
					.header("X-Loopers-LoginId", LOGIN_ID)
					.header("X-Loopers-LoginPw", PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isArray())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.totalCount").value(2));
		}


		@Test
		@DisplayName("[GET /api/v1/cart] 빈 장바구니 -> 200 OK. 빈 목록")
		void getCartEmpty() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/cart")
					.header("X-Loopers-LoginId", LOGIN_ID)
					.header("X-Loopers-LoginPw", PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isArray())
				.andExpect(jsonPath("$.items.length()").value(0))
				.andExpect(jsonPath("$.totalCount").value(0));
		}


		@Test
		@DisplayName("[GET /api/v1/cart] 인증 헤더 누락 -> 401 Unauthorized")
		void getCartFailWhenNoAuth() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/cart"))
				.andExpect(status().isUnauthorized());
		}
	}


	@Nested
	@DisplayName("GET /api/v1/cart/status - 장바구니 상태 조회")
	class GetCartStatusTest {

		@Test
		@DisplayName("[GET /api/v1/cart/status] 상태 조회 -> 200 OK. totalCount, selectedCount, items 반환")
		void getCartStatusSuccess() throws Exception {
			// Arrange - 항목 추가
			addCartItem(1L, 2L);
			addCartItem(2L, 1L);

			// Act & Assert
			mockMvc.perform(get("/api/v1/cart/status")
					.header("X-Loopers-LoginId", LOGIN_ID)
					.header("X-Loopers-LoginPw", PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCount").value(2))
				.andExpect(jsonPath("$.selectedCount").isNumber())
				.andExpect(jsonPath("$.items").isArray());
		}


		@Test
		@DisplayName("[GET /api/v1/cart/status] 인증 헤더 누락 -> 401 Unauthorized")
		void getCartStatusFailWhenNoAuth() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/cart/status"))
				.andExpect(status().isUnauthorized());
		}
	}


	/**
	 * 테스트 헬퍼
	 * 1. 사용자 생성 (API 호출)
	 * 2. 장바구니 항목 추가 (API 호출)
	 */

	// 1. 사용자 생성
	private void signUpUser(String loginId, String password, String name, LocalDate birthDate, String email) throws Exception {
		UserSignUpRequest request = new UserSignUpRequest(loginId, password, name, birthDate, email);
		mockMvc.perform(post("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}


	// 2. 장바구니 항목 추가 (API 호출) - 추가된 항목의 ID 반환
	private Long addCartItem(Long productId, Long quantity) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/cart/items")
				.header("X-Loopers-LoginId", LOGIN_ID)
				.header("X-Loopers-LoginPw", PASSWORD)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"productId", productId,
					"quantity", quantity
				))))
			.andExpect(status().isCreated())
			.andReturn();

		return objectMapper.readTree(result.getResponse().getContentAsString())
			.get("id").asLong();
	}

}
