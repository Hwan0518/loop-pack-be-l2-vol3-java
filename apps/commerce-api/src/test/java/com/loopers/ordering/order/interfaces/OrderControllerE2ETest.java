package com.loopers.ordering.order.interfaces;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.catalog.brand.interfaces.web.request.AdminBrandCreateRequest;
import com.loopers.catalog.product.interfaces.web.request.AdminProductCreateRequest;
import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
import com.loopers.coupon.coupontemplate.interfaces.web.request.AdminCreateCouponTemplateRequest;
import com.loopers.ordering.order.interfaces.web.request.OrderCreateRequest;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("OrderController E2E 테스트")
class OrderControllerE2ETest {

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
	private static final String TEST_PASSWORD = "Test1234!";

	// 공용 테스트 데이터 — @BeforeEach에서 초기화
	private Long brandId;
	private Long productId1;
	private Long productId2;


	@BeforeEach
	void setUp() throws Exception {
		// 사용자 생성
		signUpUser("testuser", TEST_PASSWORD, "테스트유저", LocalDate.of(1990, 1, 15), "test@example.com");
		signUpUser("testuser1", TEST_PASSWORD, "테스트유저일", LocalDate.of(1990, 2, 15), "test1@example.com");
		signUpUser("testuser2", TEST_PASSWORD, "테스트유저이", LocalDate.of(1990, 3, 15), "test2@example.com");
		signUpUser("testuser3", TEST_PASSWORD, "테스트유저삼", LocalDate.of(1990, 4, 15), "test3@example.com");

		// 브랜드 + 상품 생성
		brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
		productId1 = createProductAndGetId(brandId, "에어맥스", new BigDecimal("10000"), 100L, "러닝화");
		productId2 = createProductAndGetId(brandId, "에어포스", new BigDecimal("20000"), 100L, "캐주얼화");
	}


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Nested
	@DisplayName("POST /api/v1/orders - 주문 생성")
	class CreateOrderTest {

		@Test
		@DisplayName("[POST /api/v1/orders] 유효한 요청 -> 201 Created. id, userId, originalTotalPrice, discountAmount, totalPrice, items 포함")
		void createOrderSuccess() throws Exception {
			// Arrange
			Long cartItemId1 = addCartItemAndGetId("testuser", TEST_PASSWORD, productId1, 2L);
			Long cartItemId2 = addCartItemAndGetId("testuser", TEST_PASSWORD, productId2, 1L);
			OrderCreateRequest request = new OrderCreateRequest(List.of(cartItemId1, cartItemId2), "req-001", null);

			// Act & Assert
			mockMvc.perform(post("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "testuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.originalTotalPrice").isNumber())
				.andExpect(jsonPath("$.discountAmount").value(0))
				.andExpect(jsonPath("$.totalPrice").isNumber())
				.andExpect(jsonPath("$.items").isArray())
				.andExpect(jsonPath("$.items", hasSize(2)))
				.andExpect(jsonPath("$.couponSnapshot").doesNotExist());
		}


		@Test
		@DisplayName("[POST /api/v1/orders] 동일 requestId 재요청 -> 201 Created. 동일한 주문 반환 (멱등성)")
		void createOrderIdempotent() throws Exception {
			// Arrange
			Long cartItemId1 = addCartItemAndGetId("testuser", TEST_PASSWORD, productId1, 2L);
			Long cartItemId2 = addCartItemAndGetId("testuser", TEST_PASSWORD, productId2, 1L);
			OrderCreateRequest request = new OrderCreateRequest(List.of(cartItemId1, cartItemId2), "req-idempotent", null);

			// 1차 주문 생성
			MvcResult firstResult = mockMvc.perform(post("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "testuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andReturn();

			Long firstOrderId = objectMapper.readTree(firstResult.getResponse().getContentAsString()).get("id").asLong();

			// 2차 동일 요청 (장바구니 항목은 이미 삭제되었지만 멱등성으로 기존 주문 반환)
			mockMvc.perform(post("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "testuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(firstOrderId));
		}


		@Test
		@DisplayName("[POST /api/v1/orders] 인증 헤더 누락 -> 401 Unauthorized")
		void createOrderUnauthorized() throws Exception {
			// Arrange
			OrderCreateRequest request = new OrderCreateRequest(List.of(1L, 2L), "req-001", null);

			// Act & Assert
			mockMvc.perform(post("/api/v1/orders")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}


		@Test
		@DisplayName("[POST /api/v1/orders] requestId 누락 -> 400 Bad Request")
		void createOrderWithoutRequestId() throws Exception {
			// Arrange
			String requestJson = "{\"cartItemIds\": [1, 2]}";

			// Act & Assert
			mockMvc.perform(post("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "testuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[POST /api/v1/orders] cartItemIds 비어있음 -> 400 Bad Request")
		void createOrderWithEmptyCartItemIds() throws Exception {
			// Arrange
			String requestJson = "{\"cartItemIds\": [], \"requestId\": \"req-001\"}";

			// Act & Assert
			mockMvc.perform(post("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "testuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[POST /api/v1/orders] 쿠폰 적용 주문 -> 201 Created. discountAmount > 0, couponSnapshot 포함")
		void createOrderWithCouponApplied() throws Exception {
			// Arrange — 쿠폰 템플릿 생성 + 발급
			Long couponTemplateId = createCouponTemplateAndGetId("1000원 할인 쿠폰", CouponType.FIXED, new BigDecimal("1000"), new BigDecimal("5000"));
			Long issuedCouponId = issueCouponAndGetId("testuser", TEST_PASSWORD, couponTemplateId);

			// 장바구니 항목 추가 (총액: 10000 * 2 + 20000 * 1 = 40000)
			Long cartItemId1 = addCartItemAndGetId("testuser", TEST_PASSWORD, productId1, 2L);
			Long cartItemId2 = addCartItemAndGetId("testuser", TEST_PASSWORD, productId2, 1L);
			OrderCreateRequest request = new OrderCreateRequest(List.of(cartItemId1, cartItemId2), "req-coupon", issuedCouponId);

			// Act & Assert
			mockMvc.perform(post("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "testuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.originalTotalPrice").value(40000))
				.andExpect(jsonPath("$.discountAmount").value(1000))
				.andExpect(jsonPath("$.totalPrice").value(39000))
				.andExpect(jsonPath("$.couponSnapshot").isNotEmpty())
				.andExpect(jsonPath("$.couponSnapshot.issuedCouponId").value(issuedCouponId))
				.andExpect(jsonPath("$.couponSnapshot.name").value("1000원 할인 쿠폰"))
				.andExpect(jsonPath("$.couponSnapshot.type").value("FIXED"))
				.andExpect(jsonPath("$.couponSnapshot.value").value(1000));
		}


		@Test
		@DisplayName("[POST /api/v1/orders] 일부 cartItemId가 존재하지 않음 -> 404 CART_ITEM_NOT_FOUND. 요청 수와 조회 수 불일치")
		void createOrderPartialCartItemIds() throws Exception {
			// Arrange — 장바구니 항목 2개 추가
			Long cartItemId1 = addCartItemAndGetId("testuser", TEST_PASSWORD, productId1, 1L);
			Long cartItemId2 = addCartItemAndGetId("testuser", TEST_PASSWORD, productId2, 1L);

			// 존재하지 않는 ID 999 포함
			OrderCreateRequest request = new OrderCreateRequest(List.of(cartItemId1, cartItemId2, 999L), "req-partial", null);

			// Act & Assert
			mockMvc.perform(post("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "testuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.CART_ITEM_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[POST /api/v1/orders] 삭제된 상품의 장바구니 항목 주문 -> 400 EMPTY_CART. 상품 삭제 시 장바구니 항목도 삭제됨")
		void createOrderWithDeletedProduct() throws Exception {
			// Arrange — 별도 상품 생성 후 장바구니 추가
			Long tempProductId = createProductAndGetId(brandId, "한정판", new BigDecimal("50000"), 10L, "한정판 상품");
			Long cartItemId = addCartItemAndGetId("testuser", TEST_PASSWORD, tempProductId, 1L);

			// 상품 삭제 (장바구니 항목도 함께 삭제됨)
			deleteProduct(tempProductId);

			// 삭제된 장바구니 항목 ID로 주문 시도
			OrderCreateRequest request = new OrderCreateRequest(List.of(cartItemId), "req-deleted", null);

			// Act & Assert
			mockMvc.perform(post("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "testuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.EMPTY_CART.getCode()));
		}


		@Test
		@DisplayName("[POST /api/v1/orders] 재고 부족 상품 주문 -> 409 PRODUCT_OUT_OF_STOCK. 재고보다 많은 수량 주문 시 재고 부족 에러")
		void createOrderOutOfStock() throws Exception {
			// Arrange — 재고 1개 상품 생성
			Long lowStockProductId = createProductAndGetId(brandId, "한정판", new BigDecimal("50000"), 1L, "재고 1개");

			// 수량 2개로 장바구니 추가
			Long cartItemId = addCartItemAndGetId("testuser", TEST_PASSWORD, lowStockProductId, 2L);
			OrderCreateRequest request = new OrderCreateRequest(List.of(cartItemId), "req-outofstock", null);

			// Act & Assert
			mockMvc.perform(post("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "testuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(ErrorType.PRODUCT_OUT_OF_STOCK.getCode()));
		}


		@Test
		@DisplayName("[POST /api/v1/orders] 주문 성공 후 장바구니 정리 확인 -> 주문된 항목이 장바구니에서 제거됨")
		void createOrderCartCleanup() throws Exception {
			// Arrange
			Long cartItemId1 = addCartItemAndGetId("testuser", TEST_PASSWORD, productId1, 1L);
			Long cartItemId2 = addCartItemAndGetId("testuser", TEST_PASSWORD, productId2, 1L);
			OrderCreateRequest request = new OrderCreateRequest(List.of(cartItemId1, cartItemId2), "req-cleanup", null);

			// Act — 주문 생성
			mockMvc.perform(post("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "testuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated());

			// Assert — 장바구니가 비어있는지 확인
			mockMvc.perform(get("/api/v1/cart")
					.header(USER_LOGIN_ID_HEADER, "testuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isArray())
				.andExpect(jsonPath("$.items.length()").value(0))
				.andExpect(jsonPath("$.totalCount").value(0));
		}

	}


	@Nested
	@DisplayName("GET /api/v1/orders - 내 주문 목록 조회")
	class GetOrdersTest {

		@Test
		@DisplayName("[GET /api/v1/orders] 주문 없음 -> 200 OK. 빈 목록 반환")
		void getOrdersEmpty() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "testuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.content", hasSize(0)))
				.andExpect(jsonPath("$.totalElements").value(0));
		}


		@Test
		@DisplayName("[GET /api/v1/orders] 주문 생성 후 조회 -> 200 OK. 주문 목록 반환")
		void getOrdersAfterCreate() throws Exception {
			// Arrange
			createOrder("req-001", "testuser1");

			// Act & Assert
			mockMvc.perform(get("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "testuser1")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.totalElements").value(1));
		}


		@Test
		@DisplayName("[GET /api/v1/orders] 다른 사용자의 주문은 보이지 않음 -> 200 OK. 빈 목록")
		void getOrdersOnlyOwnOrders() throws Exception {
			// Arrange
			createOrder("req-001", "testuser1");

			// Act & Assert (다른 사용자로 조회)
			mockMvc.perform(get("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "testuser2")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(0)))
				.andExpect(jsonPath("$.totalElements").value(0));
		}


		@Test
		@DisplayName("[GET /api/v1/orders] 날짜 필터 적용 -> 200 OK. 오늘 생성된 주문만 반환")
		void getOrdersWithDateFilter() throws Exception {
			// Arrange
			createOrder("req-001", "testuser1");

			String today = java.time.LocalDate.now().toString();

			// Act & Assert
			mockMvc.perform(get("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "testuser1")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.param("startDate", today)
					.param("endDate", today))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.totalElements").value(1));
		}


		@Test
		@DisplayName("[GET /api/v1/orders] 과거 날짜 필터 -> 200 OK. 빈 목록 반환. 해당 기간에 주문 없음")
		void getOrdersWithPastDateFilter() throws Exception {
			// Arrange
			createOrder("req-001", "testuser1");

			// Act & Assert
			mockMvc.perform(get("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "testuser1")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.param("startDate", "2020-01-01")
					.param("endDate", "2020-01-31"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(0)))
				.andExpect(jsonPath("$.totalElements").value(0));
		}


		@Test
		@DisplayName("[GET /api/v1/orders] 인증 헤더 누락 -> 401 Unauthorized")
		void getOrdersUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/orders"))
				.andExpect(status().isUnauthorized());
		}

	}


	@Nested
	@DisplayName("GET /api/v1/orders/{orderId} - 내 주문 상세 조회")
	class GetOrderTest {

		@Test
		@DisplayName("[GET /api/v1/orders/{orderId}] 본인 주문 -> 200 OK. id, totalPrice, items 포함")
		void getOrderSuccess() throws Exception {
			// Arrange
			Long orderId = createOrderAndGetId("req-001", "testuser1");

			// Act & Assert
			mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
					.header(USER_LOGIN_ID_HEADER, "testuser1")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(orderId))
				.andExpect(jsonPath("$.items").isArray())
				.andExpect(jsonPath("$.items", hasSize(2)));
		}


		@Test
		@DisplayName("[GET /api/v1/orders/{orderId}] 다른 사용자의 주문 -> 404 Not Found")
		void getOrderAccessDenied() throws Exception {
			// Arrange
			Long orderId = createOrderAndGetId("req-001", "testuser1");

			// Act & Assert (다른 사용자로 조회)
			mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
					.header(USER_LOGIN_ID_HEADER, "testuser2")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.ORDER_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[GET /api/v1/orders/{orderId}] 존재하지 않는 주문 ID -> 404 Not Found")
		void getOrderNotFound() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/orders/{orderId}", 999L)
					.header(USER_LOGIN_ID_HEADER, "testuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.ORDER_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[GET /api/v1/orders/{orderId}] 인증 헤더 누락 -> 401 Unauthorized")
		void getOrderUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/orders/{orderId}", 1L))
				.andExpect(status().isUnauthorized());
		}


		@Test
		@DisplayName("[GET /api/v1/orders/{orderId}] 주문 후 상품 삭제 -> 200 OK. 스냅샷 기반으로 주문 상세 정상 반환")
		void getOrderAfterProductDeleted() throws Exception {
			// Arrange — 별도 상품 생성 후 주문
			Long tempProductId = createProductAndGetId(brandId, "한정판", new BigDecimal("30000"), 10L, "한정판 상품");
			Long cartItemId = addCartItemAndGetId("testuser1", TEST_PASSWORD, tempProductId, 1L);
			OrderCreateRequest request = new OrderCreateRequest(List.of(cartItemId), "req-snapshot", null);

			MvcResult result = mockMvc.perform(post("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "testuser1")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andReturn();

			Long orderId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

			// 상품 삭제
			deleteProduct(tempProductId);

			// Act & Assert — 주문 상세 조회 시 스냅샷 데이터 정상 반환
			mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
					.header(USER_LOGIN_ID_HEADER, "testuser1")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(orderId))
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].snapshotName").value("한정판"))
				.andExpect(jsonPath("$.items[0].snapshotPrice").value(30000))
				.andExpect(jsonPath("$.items[0].quantity").value(1));
		}

	}


	@Nested
	@DisplayName("GET /api-admin/v1/orders - 관리자 주문 목록 조회")
	class GetAdminOrdersTest {

		@Test
		@DisplayName("[GET /api-admin/v1/orders] LDAP 인증 성공 -> 200 OK. 전체 주문 반환")
		void getAdminOrdersSuccess() throws Exception {
			// Arrange
			createOrder("req-001", "testuser1");
			createOrder("req-002", "testuser2");

			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/orders")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.totalElements").value(2));
		}


		@Test
		@DisplayName("[GET /api-admin/v1/orders] LDAP 헤더 누락 -> 401 Unauthorized")
		void getAdminOrdersUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/orders"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}


		@Test
		@DisplayName("[GET /api-admin/v1/orders] 페이지네이션 -> 200 OK. page, size, totalElements 포함")
		void getAdminOrdersWithPagination() throws Exception {
			// Arrange
			createOrder("req-001", "testuser1");
			createOrder("req-002", "testuser2");
			createOrder("req-003", "testuser3");

			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/orders")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.param("page", "0")
					.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.totalElements").value(3));
		}

	}


	@Nested
	@DisplayName("GET /api-admin/v1/orders/{orderId} - 관리자 주문 상세 조회")
	class GetAdminOrderTest {

		@Test
		@DisplayName("[GET /api-admin/v1/orders/{orderId}] LDAP 인증 성공 -> 200 OK. 주문 상세 반환")
		void getAdminOrderSuccess() throws Exception {
			// Arrange
			Long orderId = createOrderAndGetId("req-001", "testuser1");

			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/orders/{orderId}", orderId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(orderId))
				.andExpect(jsonPath("$.items").isArray());
		}


		@Test
		@DisplayName("[GET /api-admin/v1/orders/{orderId}] 존재하지 않는 주문 ID -> 404 Not Found")
		void getAdminOrderNotFound() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/orders/{orderId}", 999L)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.ORDER_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[GET /api-admin/v1/orders/{orderId}] LDAP 헤더 누락 -> 401 Unauthorized")
		void getAdminOrderUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/orders/{orderId}", 1L))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}

	}


	/**
	 * 테스트 헬퍼 메서드
	 * 1. 사용자 생성 (API 호출)
	 * 2. 브랜드 생성 (관리자 API 호출)
	 * 3. 상품 생성 (관리자 API 호출)
	 * 4. 장바구니 항목 추가 (사용자 API 호출)
	 * 5. 주문 생성 (사용자 API 호출)
	 * 6. 주문 생성 후 ID 반환 (사용자 API 호출)
	 * 7. 상품 삭제 (관리자 API 호출)
	 * 8. 쿠폰 템플릿 생성 (관리자 API 호출)
	 * 9. 쿠폰 발급 (사용자 API 호출)
	 */

	// 1. 사용자 생성
	private void signUpUser(String loginId, String password, String name, LocalDate birthDate, String email) throws Exception {
		UserSignUpRequest request = new UserSignUpRequest(loginId, password, name, birthDate, email);
		mockMvc.perform(post("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}


	// 2. 브랜드 생성 후 ID 반환
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


	// 3. 상품 생성 후 ID 반환
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


	// 4. 장바구니 항목 추가 후 ID 반환
	private Long addCartItemAndGetId(String loginId, String password, Long productId, Long quantity) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/cart/items")
				.header(USER_LOGIN_ID_HEADER, loginId)
				.header(USER_LOGIN_PW_HEADER, password)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"productId", productId,
					"quantity", quantity
				))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}


	// 5. 주문 생성 (장바구니 추가 → 주문)
	private void createOrder(String requestId, String loginId) throws Exception {
		Long cartItemId1 = addCartItemAndGetId(loginId, TEST_PASSWORD, productId1, 2L);
		Long cartItemId2 = addCartItemAndGetId(loginId, TEST_PASSWORD, productId2, 1L);
		OrderCreateRequest request = new OrderCreateRequest(List.of(cartItemId1, cartItemId2), requestId, null);
		mockMvc.perform(post("/api/v1/orders")
				.header(USER_LOGIN_ID_HEADER, loginId)
				.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}


	// 6. 주문 생성 후 ID 반환 (장바구니 추가 → 주문)
	private Long createOrderAndGetId(String requestId, String loginId) throws Exception {
		Long cartItemId1 = addCartItemAndGetId(loginId, TEST_PASSWORD, productId1, 2L);
		Long cartItemId2 = addCartItemAndGetId(loginId, TEST_PASSWORD, productId2, 1L);
		OrderCreateRequest request = new OrderCreateRequest(List.of(cartItemId1, cartItemId2), requestId, null);
		MvcResult result = mockMvc.perform(post("/api/v1/orders")
				.header(USER_LOGIN_ID_HEADER, loginId)
				.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}


	// 7. 상품 삭제
	private void deleteProduct(Long productId) throws Exception {
		mockMvc.perform(delete("/api-admin/v1/products/{productId}", productId)
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
			.andExpect(status().isNoContent());
	}


	// 8. 쿠폰 템플릿 생성 후 ID 반환
	private Long createCouponTemplateAndGetId(String name, CouponType type, BigDecimal value, BigDecimal minOrderAmount) throws Exception {
		AdminCreateCouponTemplateRequest request = new AdminCreateCouponTemplateRequest(
			name, type, value, minOrderAmount, null, LocalDateTime.now().plusDays(30)
		);
		MvcResult result = mockMvc.perform(post("/api-admin/v1/coupons")
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}


	// 9. 쿠폰 발급 후 발급 쿠폰 ID 반환
	private Long issueCouponAndGetId(String loginId, String password, Long couponTemplateId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponTemplateId)
				.header(USER_LOGIN_ID_HEADER, loginId)
				.header(USER_LOGIN_PW_HEADER, password))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("issuedCouponId").asLong();
	}

}
