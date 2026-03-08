package com.loopers.engagement.productlike.interfaces;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.catalog.brand.interfaces.web.request.AdminBrandCreateRequest;
import com.loopers.catalog.product.interfaces.web.request.AdminProductCreateRequest;
import com.loopers.support.common.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.user.user.interfaces.web.request.UserSignUpRequest;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("ProductLikeController E2E 테스트")
class ProductLikeControllerE2ETest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	private static final String USER_LOGIN_ID_HEADER = "X-Loopers-LoginId";
	private static final String USER_LOGIN_PW_HEADER = "X-Loopers-LoginPw";
	private static final String ADMIN_LDAP_HEADER = "X-Loopers-Ldap";
	private static final String ADMIN_LDAP_VALUE = "loopers.admin";

	// 테스트 사용자 기본값
	private static final String TEST_LOGIN_ID = "testuser01";
	private static final String TEST_PASSWORD = "Test1234!";


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Nested
	@DisplayName("POST /api/v1/products/{id}/likes - 상품 좋아요 생성")
	class CreateProductLikeTest {

		@Test
		@DisplayName("[POST /api/v1/products/{id}/likes] 유효한 좋아요 생성 -> 200 OK. id, userId, targetId 포함")
		void createProductLikeSuccess() throws Exception {
			// Arrange
			signUpUser(TEST_LOGIN_ID, TEST_PASSWORD, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
			Long productId = createProductWithBrand();

			// Act & Assert
			mockMvc.perform(post("/api/v1/products/{id}/likes", productId)
					.header(USER_LOGIN_ID_HEADER, TEST_LOGIN_ID)
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.userId").isNumber())
				.andExpect(jsonPath("$.targetId").value(productId))
				.andExpect(jsonPath("$.createdAt").exists());
		}


		@Test
		@DisplayName("[POST /api/v1/products/{id}/likes] 동일 사용자 동일 상품 좋아요 중복 요청 -> 200 OK. 멱등 반환, 기존 좋아요의 동일 id 반환")
		void createProductLikeIdempotent() throws Exception {
			// Arrange
			signUpUser(TEST_LOGIN_ID, TEST_PASSWORD, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
			Long productId = createProductWithBrand();

			// 1차 좋아요 생성
			MvcResult firstResult = mockMvc.perform(post("/api/v1/products/{id}/likes", productId)
					.header(USER_LOGIN_ID_HEADER, TEST_LOGIN_ID)
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk())
				.andReturn();

			Long firstLikeId = objectMapper.readTree(firstResult.getResponse().getContentAsString()).get("id").asLong();

			// Act — 2차 동일 좋아요 요청 (멱등)
			mockMvc.perform(post("/api/v1/products/{id}/likes", productId)
					.header(USER_LOGIN_ID_HEADER, TEST_LOGIN_ID)
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(firstLikeId))
				.andExpect(jsonPath("$.targetId").value(productId));
		}


		@Test
		@DisplayName("[POST /api/v1/products/{id}/likes] 인증 헤더 누락 -> 401 Unauthorized")
		void createProductLikeUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(post("/api/v1/products/{id}/likes", 1L))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}

	}


	@Nested
	@DisplayName("DELETE /api/v1/products/{id}/likes - 상품 좋아요 삭제")
	class DeleteProductLikeTest {

		@Test
		@DisplayName("[DELETE /api/v1/products/{id}/likes] 유효한 좋아요 삭제 -> 200 OK")
		void deleteProductLikeSuccess() throws Exception {
			// Arrange
			signUpUser(TEST_LOGIN_ID, TEST_PASSWORD, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
			Long productId = createProductWithBrand();

			// 좋아요 생성
			mockMvc.perform(post("/api/v1/products/{id}/likes", productId)
					.header(USER_LOGIN_ID_HEADER, TEST_LOGIN_ID)
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk());

			// Act & Assert
			mockMvc.perform(delete("/api/v1/products/{id}/likes", productId)
					.header(USER_LOGIN_ID_HEADER, TEST_LOGIN_ID)
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk());
		}


		@Test
		@DisplayName("[DELETE /api/v1/products/{id}/likes] 존재하지 않는 좋아요 삭제 -> 404 Not Found. LIKE_NOT_FOUND")
		void deleteProductLikeNotFound() throws Exception {
			// Arrange
			signUpUser(TEST_LOGIN_ID, TEST_PASSWORD, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
			Long productId = createProductWithBrand();

			// Act & Assert — 좋아요 생성 없이 삭제 시도
			mockMvc.perform(delete("/api/v1/products/{id}/likes", productId)
					.header(USER_LOGIN_ID_HEADER, TEST_LOGIN_ID)
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.LIKE_NOT_FOUND.getCode()));
		}

	}


	/**
	 * 테스트 헬퍼 메서드
	 */

	// 사용자 생성
	private void signUpUser(String loginId, String password, String name, LocalDate birthDate, String email) throws Exception {
		UserSignUpRequest request = new UserSignUpRequest(loginId, password, name, birthDate, email);
		mockMvc.perform(post("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}


	// 브랜드 생성 후 ID 반환 (관리자)
	private Long createBrandAndGetId(String name, String description) throws Exception {
		AdminBrandCreateRequest request = new AdminBrandCreateRequest(name, description);
		MvcResult result = mockMvc.perform(post("/api-admin/v1/brands")
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}


	// 상품 생성 후 ID 반환 (관리자)
	private Long createProductAndGetId(Long brandId, String name, BigDecimal price, Long stock, String description) throws Exception {
		AdminProductCreateRequest request = new AdminProductCreateRequest(brandId, name, price, stock, description);
		MvcResult result = mockMvc.perform(post("/api-admin/v1/products")
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}


	// 브랜드 + 상품 생성 후 상품 ID 반환 (편의 메서드)
	private Long createProductWithBrand() throws Exception {
		Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
		return createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");
	}

}
