package com.loopers.catalog.product.interfaces;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.interfaces.web.request.AdminBrandCreateRequest;

import com.loopers.catalog.product.interfaces.web.request.AdminProductCreateRequest;
import com.loopers.catalog.product.interfaces.web.request.AdminProductUpdateRequest;
import com.loopers.support.common.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("ProductController E2E 테스트")
class ProductControllerE2ETest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	@Autowired
	private RedisCleanUp redisCleanUp;

	private static final String ADMIN_LDAP_HEADER = "X-Loopers-Ldap";
	private static final String ADMIN_LDAP_VALUE = "loopers.admin";


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
		redisCleanUp.truncateAll();
	}


	@Nested
	@DisplayName("GET /api/v1/products - 상품 목록 조회 (공개, 활성 상품만)")
	class GetProductsTest {

		@Test
		@DisplayName("[GET /api/v1/products] 상품 없음 -> 200 OK. 빈 목록 반환")
		void getProductsEmpty() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.content", hasSize(0)))
				.andExpect(jsonPath("$.totalElements").value(0));
		}


		@Test
		@DisplayName("[GET /api/v1/products] 활성 상품 2개 -> 200 OK. 2개 반환")
		void getProductsList() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			createProduct(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");
			createProduct(brandId, "에어포스", new BigDecimal("139000"), 50L, "캐주얼화");

			// Act & Assert
			mockMvc.perform(get("/api/v1/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.totalElements").value(2));
		}


		@Test
		@DisplayName("[GET /api/v1/products] 삭제된 상품 제외 -> 200 OK. 활성 상품만 반환")
		void getProductsExcludesDeleted() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId1 = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");
			createProduct(brandId, "에어포스", new BigDecimal("139000"), 50L, "캐주얼화");
			deleteProduct(productId1);

			// Act & Assert
			mockMvc.perform(get("/api/v1/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.totalElements").value(1));
		}


		@Test
		@DisplayName("[GET /api/v1/products] brandId 필터 -> 200 OK. 해당 브랜드 상품만 반환")
		void getProductsFilterByBrandId() throws Exception {
			// Arrange
			Long brandId1 = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long brandId2 = createBrandAndGetId("아디다스", "독일 스포츠 브랜드");
			createProduct(brandId1, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");
			createProduct(brandId2, "울트라부스트", new BigDecimal("199000"), 30L, "러닝화");

			// Act & Assert
			mockMvc.perform(get("/api/v1/products")
					.param("brandId", brandId1.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].name").value("에어맥스"))
				.andExpect(jsonPath("$.totalElements").value(1));
		}


		@Test
		@DisplayName("[GET /api/v1/products] 페이지네이션 -> 200 OK. page=0, size=1일 때 1개 반환, totalElements=2")
		void getProductsWithPagination() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			createProduct(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");
			createProduct(brandId, "에어포스", new BigDecimal("139000"), 50L, "캐주얼화");

			// Act & Assert
			mockMvc.perform(get("/api/v1/products")
					.param("page", "0")
					.param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.totalElements").value(2));
		}


		@Test
		@DisplayName("[GET /api/v1/products] sort=PRICE_ASC -> 200 OK. 가격 오름차순 정렬")
		void getProductsSortByPriceAsc() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			createProduct(brandId, "에어포스", new BigDecimal("139000"), 50L, "캐주얼화");
			createProduct(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");

			// Act & Assert
			mockMvc.perform(get("/api/v1/products")
					.param("sort", "PRICE_ASC"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.content[0].name").value("에어맥스"))
				.andExpect(jsonPath("$.content[1].name").value("에어포스"));
		}


		@Test
		@DisplayName("[GET /api/v1/products] 인증 없이 조회 가능 -> 200 OK")
		void getProductsWithoutAuth() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/products"))
				.andExpect(status().isOk());
		}

	}


	@Nested
	@DisplayName("GET /api/v1/products/{productId} - 상품 상세 조회 (공개, 활성 상품만)")
	class GetProductTest {

		@Test
		@DisplayName("[GET /api/v1/products/{productId}] 활성 상품 -> 200 OK. id, brandId, brandName, name, price, stock, description, likeCount 포함")
		void getProductSuccess() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");

			// Act & Assert
			mockMvc.perform(get("/api/v1/products/{productId}", productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(productId))
				.andExpect(jsonPath("$.brandId").value(brandId))
				.andExpect(jsonPath("$.brandName").value("나이키"))
				.andExpect(jsonPath("$.name").value("에어맥스"))
				.andExpect(jsonPath("$.price").value(129000))
				.andExpect(jsonPath("$.stock").value(100))
				.andExpect(jsonPath("$.description").value("러닝화"))
				.andExpect(jsonPath("$.likeCount").value(0));
		}


		@Test
		@DisplayName("[GET /api/v1/products/{productId}] 존재하지 않는 ID -> 404 Not Found. PRODUCT_NOT_FOUND")
		void getProductNotFound() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/products/{productId}", 999L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.PRODUCT_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[GET /api/v1/products/{productId}] 삭제된 상품 -> 404 Not Found. PRODUCT_NOT_FOUND")
		void getProductSoftDeleted() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");
			deleteProduct(productId);

			// Act & Assert
			mockMvc.perform(get("/api/v1/products/{productId}", productId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.PRODUCT_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[GET /api/v1/products/{productId}] 인증 없이 조회 가능 -> 200 OK")
		void getProductWithoutAuth() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");

			// Act & Assert
			mockMvc.perform(get("/api/v1/products/{productId}", productId))
				.andExpect(status().isOk());
		}

	}


	@Nested
	@DisplayName("GET /api-admin/v1/products - 상품 목록 조회 (관리자)")
	class GetAdminProductsTest {

		@Test
		@DisplayName("[GET /api-admin/v1/products] LDAP 인증 성공, 전체 상품 -> 200 OK. 삭제된 상품 포함 반환")
		void getAdminProductsAll() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId1 = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");
			createProduct(brandId, "에어포스", new BigDecimal("139000"), 50L, "캐주얼화");
			deleteProduct(productId1);

			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/products")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.totalElements").value(2));
		}


		@Test
		@DisplayName("[GET /api-admin/v1/products] brandId 필터 -> 200 OK. 해당 브랜드 상품만 반환")
		void getAdminProductsFilterByBrandId() throws Exception {
			// Arrange
			Long brandId1 = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long brandId2 = createBrandAndGetId("아디다스", "독일 스포츠 브랜드");
			createProduct(brandId1, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");
			createProduct(brandId2, "울트라부스트", new BigDecimal("199000"), 30L, "러닝화");

			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/products")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.param("brandId", brandId1.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].name").value("에어맥스"))
				.andExpect(jsonPath("$.totalElements").value(1));
		}


		@Test
		@DisplayName("[GET /api-admin/v1/products] LDAP 헤더 누락 -> 401 Unauthorized")
		void getAdminProductsUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/products"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}

	}


	@Nested
	@DisplayName("GET /api-admin/v1/products/{productId} - 상품 상세 조회 (관리자)")
	class GetAdminProductTest {

		@Test
		@DisplayName("[GET /api-admin/v1/products/{productId}] 활성 상품 -> 200 OK. id, brandId, brandName, name, price, stock, description, likeCount, deletedAt 포함")
		void getAdminProductActive() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");

			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/products/{productId}", productId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(productId))
				.andExpect(jsonPath("$.brandId").value(brandId))
				.andExpect(jsonPath("$.brandName").value("나이키"))
				.andExpect(jsonPath("$.name").value("에어맥스"))
				.andExpect(jsonPath("$.price").value(129000))
				.andExpect(jsonPath("$.stock").value(100))
				.andExpect(jsonPath("$.description").value("러닝화"))
				.andExpect(jsonPath("$.likeCount").value(0))
				.andExpect(jsonPath("$.deletedAt").doesNotExist());
		}


		@Test
		@DisplayName("[GET /api-admin/v1/products/{productId}] 삭제된 상품도 조회 가능 -> 200 OK. deletedAt 존재")
		void getAdminProductDeleted() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");
			deleteProduct(productId);

			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/products/{productId}", productId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(productId))
				.andExpect(jsonPath("$.deletedAt").exists());
		}


		@Test
		@DisplayName("[GET /api-admin/v1/products/{productId}] 존재하지 않는 ID -> 404 Not Found")
		void getAdminProductNotFound() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/products/{productId}", 999L)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.PRODUCT_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[GET /api-admin/v1/products/{productId}] LDAP 헤더 누락 -> 401 Unauthorized")
		void getAdminProductUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/products/{productId}", 1L))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}

	}


	@Nested
	@DisplayName("POST /api-admin/v1/products - 상품 생성")
	class CreateProductTest {

		@Test
		@DisplayName("[POST /api-admin/v1/products] 유효한 요청 -> 201 Created. id, brandId, brandName, name, price, stock, description, likeCount=0, deletedAt=null 포함")
		void createProductSuccess() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			AdminProductCreateRequest request = new AdminProductCreateRequest(
				brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/products")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.brandId").value(brandId))
				.andExpect(jsonPath("$.brandName").value("나이키"))
				.andExpect(jsonPath("$.name").value("에어맥스"))
				.andExpect(jsonPath("$.price").value(129000))
				.andExpect(jsonPath("$.stock").value(100))
				.andExpect(jsonPath("$.description").value("러닝화"))
				.andExpect(jsonPath("$.likeCount").value(0))
				.andExpect(jsonPath("$.deletedAt").doesNotExist());
		}


		@Test
		@DisplayName("[POST /api-admin/v1/products] description null -> 201 Created. description=null")
		void createProductWithNullDescription() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			AdminProductCreateRequest request = new AdminProductCreateRequest(
				brandId, "에어맥스", new BigDecimal("129000"), 100L, null);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/products")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("에어맥스"))
				.andExpect(jsonPath("$.description").doesNotExist());
		}


		@Test
		@DisplayName("[POST /api-admin/v1/products] name 앞뒤 공백 -> trim 처리 후 201 Created")
		void createProductTrimsName() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			AdminProductCreateRequest request = new AdminProductCreateRequest(
				brandId, "  에어맥스  ", new BigDecimal("129000"), 100L, "러닝화");

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/products")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("에어맥스"));
		}


		@Test
		@DisplayName("[POST /api-admin/v1/products] LDAP 헤더 누락 -> 401 Unauthorized")
		void createProductUnauthorized() throws Exception {
			// Arrange
			AdminProductCreateRequest request = new AdminProductCreateRequest(
				1L, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/products")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}


		@Test
		@DisplayName("[POST /api-admin/v1/products] name 누락 -> 400 Bad Request")
		void createProductFailNameMissing() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			String requestJson = """
			                     {
			                     	"brandId": %d,
			                     	"price": 129000,
			                     	"stock": 100,
			                     	"description": "러닝화"
			                     }
			                     """.formatted(brandId);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/products")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[POST /api-admin/v1/products] brandId 누락 -> 400 Bad Request")
		void createProductFailBrandIdMissing() throws Exception {
			// Arrange
			String requestJson = """
			                     {
			                     	"name": "에어맥스",
			                     	"price": 129000,
			                     	"stock": 100,
			                     	"description": "러닝화"
			                     }
			                     """;

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/products")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[POST /api-admin/v1/products] price 누락 -> 400 Bad Request")
		void createProductFailPriceMissing() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			String requestJson = """
			                     {
			                     	"brandId": %d,
			                     	"name": "에어맥스",
			                     	"stock": 100,
			                     	"description": "러닝화"
			                     }
			                     """.formatted(brandId);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/products")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[POST /api-admin/v1/products] stock 누락 -> 400 Bad Request")
		void createProductFailStockMissing() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			String requestJson = """
			                     {
			                     	"brandId": %d,
			                     	"name": "에어맥스",
			                     	"price": 129000,
			                     	"description": "러닝화"
			                     }
			                     """.formatted(brandId);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/products")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[POST /api-admin/v1/products] name이 200자 초과 -> 400 Bad Request. INVALID_PRODUCT_NAME")
		void createProductFailNameTooLong() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			String longName = "가".repeat(201);
			AdminProductCreateRequest request = new AdminProductCreateRequest(
				brandId, longName, new BigDecimal("129000"), 100L, "러닝화");

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/products")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_PRODUCT_NAME.getCode()));
		}


		@Test
		@DisplayName("[POST /api-admin/v1/products] 음수 가격 -> 400 Bad Request. INVALID_PRODUCT_PRICE")
		void createProductFailNegativePrice() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			AdminProductCreateRequest request = new AdminProductCreateRequest(
				brandId, "에어맥스", new BigDecimal("-1"), 100L, "러닝화");

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/products")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_PRODUCT_PRICE.getCode()));
		}


		@Test
		@DisplayName("[POST /api-admin/v1/products] 음수 재고 -> 400 Bad Request. INVALID_PRODUCT_STOCK")
		void createProductFailNegativeStock() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			AdminProductCreateRequest request = new AdminProductCreateRequest(
				brandId, "에어맥스", new BigDecimal("129000"), -1L, "러닝화");

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/products")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_PRODUCT_STOCK.getCode()));
		}


		@Test
		@DisplayName("[POST /api-admin/v1/products] 존재하지 않는 brandId -> 404 Not Found. BRAND_NOT_FOUND")
		void createProductFailBrandNotFound() throws Exception {
			// Arrange
			AdminProductCreateRequest request = new AdminProductCreateRequest(
				999L, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/products")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.BRAND_NOT_FOUND.getCode()));
		}

	}


	@Nested
	@DisplayName("PUT /api-admin/v1/products/{productId} - 상품 수정")
	class UpdateProductTest {

		@Test
		@DisplayName("[PUT /api-admin/v1/products/{productId}] 유효한 요청 -> 200 OK. 수정된 name, price, stock, description 반환")
		void updateProductSuccess() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");
			AdminProductUpdateRequest request = new AdminProductUpdateRequest(
				"에어맥스 97", new BigDecimal("159000"), 200L, "레트로 러닝화");

			// Act & Assert
			mockMvc.perform(put("/api-admin/v1/products/{productId}", productId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(productId))
				.andExpect(jsonPath("$.brandId").value(brandId))
				.andExpect(jsonPath("$.brandName").value("나이키"))
				.andExpect(jsonPath("$.name").value("에어맥스 97"))
				.andExpect(jsonPath("$.price").value(159000))
				.andExpect(jsonPath("$.stock").value(200))
				.andExpect(jsonPath("$.description").value("레트로 러닝화"));
		}


		@Test
		@DisplayName("[PUT /api-admin/v1/products/{productId}] description을 null로 수정 -> 200 OK. description=null")
		void updateProductDescriptionToNull() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");
			AdminProductUpdateRequest request = new AdminProductUpdateRequest(
				"에어맥스", new BigDecimal("129000"), 100L, null);

			// Act & Assert
			mockMvc.perform(put("/api-admin/v1/products/{productId}", productId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").doesNotExist());
		}


		@Test
		@DisplayName("[PUT /api-admin/v1/products/{productId}] LDAP 헤더 누락 -> 401 Unauthorized")
		void updateProductUnauthorized() throws Exception {
			// Arrange
			AdminProductUpdateRequest request = new AdminProductUpdateRequest(
				"에어맥스 97", new BigDecimal("159000"), 200L, "레트로 러닝화");

			// Act & Assert
			mockMvc.perform(put("/api-admin/v1/products/{productId}", 1L)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}


		@Test
		@DisplayName("[PUT /api-admin/v1/products/{productId}] 존재하지 않는 ID -> 404 Not Found. PRODUCT_NOT_FOUND")
		void updateProductNotFound() throws Exception {
			// Arrange
			AdminProductUpdateRequest request = new AdminProductUpdateRequest(
				"에어맥스 97", new BigDecimal("159000"), 200L, "레트로 러닝화");

			// Act & Assert
			mockMvc.perform(put("/api-admin/v1/products/{productId}", 999L)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.PRODUCT_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[PUT /api-admin/v1/products/{productId}] name 누락 -> 400 Bad Request")
		void updateProductFailNameMissing() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");
			String requestJson = """
			                     {
			                     	"price": 159000,
			                     	"stock": 200,
			                     	"description": "레트로 러닝화"
			                     }
			                     """;

			// Act & Assert
			mockMvc.perform(put("/api-admin/v1/products/{productId}", productId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[PUT /api-admin/v1/products/{productId}] 삭제된 상품 수정 -> 404 Not Found. PRODUCT_NOT_FOUND")
		void updateProductSoftDeleted() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");
			deleteProduct(productId);
			AdminProductUpdateRequest request = new AdminProductUpdateRequest(
				"에어맥스 97", new BigDecimal("159000"), 200L, "레트로 러닝화");

			// Act & Assert
			mockMvc.perform(put("/api-admin/v1/products/{productId}", productId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.PRODUCT_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[PUT + GET /api/v1/products/{productId}] 상품 수정 후 상세 조회 -> 캐시 무효화되어 수정된 데이터 반환")
		void updateProductThenGetReturnsUpdatedData() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");

			// 상세 조회 (캐시에 저장됨)
			mockMvc.perform(get("/api/v1/products/{productId}", productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("에어맥스"));

			// 상품 수정 (캐시 무효화 발생)
			AdminProductUpdateRequest updateRequest = new AdminProductUpdateRequest(
				"에어맥스 97", new BigDecimal("159000"), 200L, "레트로 러닝화");
			mockMvc.perform(put("/api-admin/v1/products/{productId}", productId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(updateRequest)))
				.andExpect(status().isOk());

			// Act & Assert — 수정된 데이터가 반환되어야 함 (캐시 무효화 검증)
			mockMvc.perform(get("/api/v1/products/{productId}", productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("에어맥스 97"))
				.andExpect(jsonPath("$.price").value(159000))
				.andExpect(jsonPath("$.stock").value(200))
				.andExpect(jsonPath("$.description").value("레트로 러닝화"));
		}

	}


	@Nested
	@DisplayName("DELETE /api-admin/v1/products/{productId} - 상품 삭제")
	class DeleteProductTest {

		@Test
		@DisplayName("[DELETE /api-admin/v1/products/{productId}] 유효한 요청 -> 204 No Content")
		void deleteProductSuccess() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");

			// Act & Assert
			mockMvc.perform(delete("/api-admin/v1/products/{productId}", productId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isNoContent());
		}


		@Test
		@DisplayName("[DELETE /api-admin/v1/products/{productId}] 삭제 후 공개 조회 -> 404 Not Found. PRODUCT_NOT_FOUND")
		void deleteProductThenGetReturns404() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");
			deleteProduct(productId);

			// Act & Assert
			mockMvc.perform(get("/api/v1/products/{productId}", productId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.PRODUCT_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[DELETE /api-admin/v1/products/{productId}] 삭제 후 관리자 조회 -> 200 OK. deletedAt 존재")
		void deleteProductThenAdminGetReturnsWithDeletedAt() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");
			deleteProduct(productId);

			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/products/{productId}", productId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(productId))
				.andExpect(jsonPath("$.deletedAt").exists());
		}


		@Test
		@DisplayName("[DELETE /api-admin/v1/products/{productId}] LDAP 헤더 누락 -> 401 Unauthorized")
		void deleteProductUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(delete("/api-admin/v1/products/{productId}", 1L))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}


		@Test
		@DisplayName("[DELETE /api-admin/v1/products/{productId}] 존재하지 않는 ID -> 404 Not Found. PRODUCT_NOT_FOUND")
		void deleteProductNotFound() throws Exception {
			// Act & Assert
			mockMvc.perform(delete("/api-admin/v1/products/{productId}", 999L)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.PRODUCT_NOT_FOUND.getCode()));
		}

	}


	/**
	 * 테스트 헬퍼 메서드
	 */

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


	private void createProduct(Long brandId, String name, BigDecimal price, Long stock, String description) throws Exception {
		AdminProductCreateRequest request = new AdminProductCreateRequest(brandId, name, price, stock, description);
		mockMvc.perform(post("/api-admin/v1/products")
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}


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


	private void deleteProduct(Long productId) throws Exception {
		mockMvc.perform(delete("/api-admin/v1/products/{productId}", productId)
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
			.andExpect(status().isNoContent());
	}

}
