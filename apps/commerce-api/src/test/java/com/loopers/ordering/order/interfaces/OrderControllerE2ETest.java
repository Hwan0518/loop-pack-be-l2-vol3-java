package com.loopers.ordering.order.interfaces;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.ordering.order.infrastructure.entity.OrderEntity;
import com.loopers.ordering.order.infrastructure.entity.OrderItemEntity;
import com.loopers.ordering.order.infrastructure.jpa.OrderItemJpaRepository;
import com.loopers.ordering.order.infrastructure.jpa.OrderJpaRepository;
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

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class, OrderPortTestConfig.class})
@DisplayName("OrderController E2E 테스트")
class OrderControllerE2ETest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	@Autowired
	private OrderJpaRepository orderJpaRepository;

	@Autowired
	private OrderItemJpaRepository orderItemJpaRepository;

	private static final String USER_LOGIN_ID_HEADER = "X-Loopers-LoginId";
	private static final String USER_LOGIN_PW_HEADER = "X-Loopers-LoginPw";
	private static final String ADMIN_LDAP_HEADER = "X-Loopers-Ldap";
	private static final String ADMIN_LDAP_VALUE = "loopers.admin";
	private static final String TEST_PASSWORD = "Test1234!";


	@BeforeEach
	void setUp() throws Exception {
		signUpUser("testuser", TEST_PASSWORD, "테스트유저", LocalDate.of(1990, 1, 15), "test@example.com");
		signUpUser("testuser1", TEST_PASSWORD, "테스트유저일", LocalDate.of(1990, 2, 15), "test1@example.com");
		signUpUser("testuser2", TEST_PASSWORD, "테스트유저이", LocalDate.of(1990, 3, 15), "test2@example.com");
		signUpUser("testuser3", TEST_PASSWORD, "테스트유저삼", LocalDate.of(1990, 4, 15), "test3@example.com");
	}


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Nested
	@DisplayName("POST /api/v1/orders - 주문 생성")
	class CreateOrderTest {

		@Test
		@DisplayName("[POST /api/v1/orders] 유효한 요청 -> 201 Created. id, userId, totalPrice, items 포함")
		void createOrderSuccess() throws Exception {
			// Arrange
			OrderCreateRequest request = new OrderCreateRequest(List.of(1L, 2L), "req-001", null);

			// Act & Assert
			mockMvc.perform(post("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "testuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.totalPrice").isNumber())
				.andExpect(jsonPath("$.items").isArray())
				.andExpect(jsonPath("$.items", hasSize(2)));
		}


		@Test
		@DisplayName("[POST /api/v1/orders] 동일 requestId 재요청 -> 201 Created. 동일한 주문 반환 (멱등성)")
		void createOrderIdempotent() throws Exception {
			// Arrange
			OrderCreateRequest request = new OrderCreateRequest(List.of(1L, 2L), "req-idempotent", null);

			// 1차 주문 생성
			MvcResult firstResult = mockMvc.perform(post("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "testuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andReturn();

			Long firstOrderId = objectMapper.readTree(firstResult.getResponse().getContentAsString()).get("id").asLong();

			// 2차 동일 요청
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
			createOrder("req-001", 1L);

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
			createOrder("req-001", 1L);

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
			createOrder("req-001", 1L);

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
			createOrder("req-001", 1L);

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
			Long orderId = createOrderAndGetId("req-001", 1L);

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
			Long orderId = createOrderAndGetId("req-001", 1L);

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

	}


	@Nested
	@DisplayName("GET /api-admin/v1/orders - 관리자 주문 목록 조회")
	class GetAdminOrdersTest {

		@Test
		@DisplayName("[GET /api-admin/v1/orders] LDAP 인증 성공 -> 200 OK. 전체 주문 반환")
		void getAdminOrdersSuccess() throws Exception {
			// Arrange
			createOrder("req-001", 1L);
			createOrder("req-002", 2L);

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
			createOrder("req-001", 1L);
			createOrder("req-002", 2L);
			createOrder("req-003", 3L);

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
			Long orderId = createOrderAndGetId("req-001", 1L);

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
	 * 2. 주문 생성 (API 호출)
	 * 3. 주문 생성 후 ID 반환 (API 호출)
	 */

	// 1. 사용자 생성
	private void signUpUser(String loginId, String password, String name, LocalDate birthDate, String email) throws Exception {
		UserSignUpRequest request = new UserSignUpRequest(loginId, password, name, birthDate, email);
		mockMvc.perform(post("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}


	// 2. 주문 생성
	private void createOrder(String requestId, Long userId) throws Exception {
		OrderCreateRequest request = new OrderCreateRequest(List.of(1L, 2L), requestId, null);
		mockMvc.perform(post("/api/v1/orders")
				.header(USER_LOGIN_ID_HEADER, "testuser" + userId)
				.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}


	// 3. 주문 생성 후 ID 반환
	private Long createOrderAndGetId(String requestId, Long userId) throws Exception {
		OrderCreateRequest request = new OrderCreateRequest(List.of(1L, 2L), requestId, null);
		MvcResult result = mockMvc.perform(post("/api/v1/orders")
				.header(USER_LOGIN_ID_HEADER, "testuser" + userId)
				.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

}
