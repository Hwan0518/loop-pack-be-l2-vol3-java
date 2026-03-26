package com.loopers.coupon.coupontemplate.interfaces;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
import com.loopers.coupon.coupontemplate.interfaces.web.request.AdminCreateCouponTemplateRequest;
import com.loopers.coupon.coupontemplate.interfaces.web.request.AdminUpdateCouponTemplateRequest;
import com.loopers.coupon.issuedcoupon.infrastructure.cache.CaffeineCouponIssueDuplicateGuard;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("CouponTemplateAdminController E2E 테스트")
class CouponTemplateAdminControllerE2ETest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	@Autowired
	private CaffeineCouponIssueDuplicateGuard couponIssueDuplicateGuard;

	private static final String ADMIN_LDAP_HEADER = "X-Loopers-Ldap";
	private static final String ADMIN_LDAP_VALUE = "loopers.admin";
	private static final String USER_LOGIN_ID_HEADER = "X-Loopers-LoginId";
	private static final String USER_LOGIN_PW_HEADER = "X-Loopers-LoginPw";

	// 테스트 사용자 기본값
	private static final String TEST_LOGIN_ID = "testuser01";
	private static final String TEST_PASSWORD = "Test1234!";


	@SuppressWarnings("unchecked")
	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();

		// 로컬 캐시 초기화 (TRUNCATE로 auto-increment가 리셋되어 동일 userId:couponTemplateId 키가 재사용되므로)
		Cache<String, Boolean> cache = (Cache<String, Boolean>) ReflectionTestUtils.getField(
			couponIssueDuplicateGuard, "cache");
		if (cache != null) {
			cache.invalidateAll();
		}
	}


	@Nested
	@DisplayName("POST /api-admin/v1/coupons - 쿠폰 템플릿 생성")
	class CreateCouponTemplateTest {

		@Test
		@DisplayName("[POST /api-admin/v1/coupons] FIXED 타입 유효한 요청 -> 201 Created. id, name, type, value, minOrderAmount, expiredAt, deletedAt=null 포함")
		void createCouponTemplateFixedSuccess() throws Exception {
			// Arrange
			LocalDateTime expiredAt = LocalDateTime.now().plusDays(30);
			AdminCreateCouponTemplateRequest request = new AdminCreateCouponTemplateRequest(
				"신규가입 5000원 할인", CouponType.FIXED, new BigDecimal("5000"),
				new BigDecimal("10000"), null, expiredAt
			);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/coupons")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value("신규가입 5000원 할인"))
				.andExpect(jsonPath("$.type").value("FIXED"))
				.andExpect(jsonPath("$.value").value(5000))
				.andExpect(jsonPath("$.minOrderAmount").value(10000))
				.andExpect(jsonPath("$.expiredAt").exists())
				.andExpect(jsonPath("$.deletedAt").doesNotExist());
		}


		@Test
		@DisplayName("[POST /api-admin/v1/coupons] RATE 타입, minOrderAmount=null 유효한 요청 -> 201 Created. type=RATE, minOrderAmount=null")
		void createCouponTemplateRateSuccess() throws Exception {
			// Arrange
			LocalDateTime expiredAt = LocalDateTime.now().plusDays(30);
			AdminCreateCouponTemplateRequest request = new AdminCreateCouponTemplateRequest(
				"10% 할인 쿠폰", CouponType.RATE, new BigDecimal("10"),
				null, null, expiredAt
			);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/coupons")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value("10% 할인 쿠폰"))
				.andExpect(jsonPath("$.type").value("RATE"))
				.andExpect(jsonPath("$.value").value(10))
				.andExpect(jsonPath("$.minOrderAmount").doesNotExist())
				.andExpect(jsonPath("$.deletedAt").doesNotExist());
		}


		@Test
		@DisplayName("[POST /api-admin/v1/coupons] LDAP 헤더 누락 -> 401 Unauthorized")
		void createCouponTemplateUnauthorized() throws Exception {
			// Arrange
			LocalDateTime expiredAt = LocalDateTime.now().plusDays(30);
			AdminCreateCouponTemplateRequest request = new AdminCreateCouponTemplateRequest(
				"테스트 쿠폰", CouponType.FIXED, new BigDecimal("5000"),
				new BigDecimal("10000"), null, expiredAt
			);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/coupons")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}


		@Test
		@DisplayName("[POST /api-admin/v1/coupons] name 빈 문자열 -> 400 Bad Request")
		void createCouponTemplateFailNameBlank() throws Exception {
			// Arrange
			LocalDateTime expiredAt = LocalDateTime.now().plusDays(30);
			String requestJson = """
			                     {
			                     	"name": "",
			                     	"type": "FIXED",
			                     	"value": 5000,
			                     	"minOrderAmount": 10000,
			                     	"expiredAt": "%s"
			                     }
			                     """.formatted(expiredAt.toString());

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/coupons")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[POST /api-admin/v1/coupons] type 누락 -> 400 Bad Request")
		void createCouponTemplateFailTypeNull() throws Exception {
			// Arrange
			LocalDateTime expiredAt = LocalDateTime.now().plusDays(30);
			String requestJson = """
			                     {
			                     	"name": "테스트 쿠폰",
			                     	"value": 5000,
			                     	"minOrderAmount": 10000,
			                     	"expiredAt": "%s"
			                     }
			                     """.formatted(expiredAt.toString());

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/coupons")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[POST /api-admin/v1/coupons] value 누락 -> 400 Bad Request")
		void createCouponTemplateFailValueNull() throws Exception {
			// Arrange
			LocalDateTime expiredAt = LocalDateTime.now().plusDays(30);
			String requestJson = """
			                     {
			                     	"name": "테스트 쿠폰",
			                     	"type": "FIXED",
			                     	"minOrderAmount": 10000,
			                     	"expiredAt": "%s"
			                     }
			                     """.formatted(expiredAt.toString());

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/coupons")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[POST /api-admin/v1/coupons] expiredAt 누락 -> 400 Bad Request")
		void createCouponTemplateFailExpiredAtNull() throws Exception {
			// Arrange
			String requestJson = """
			                     {
			                     	"name": "테스트 쿠폰",
			                     	"type": "FIXED",
			                     	"value": 5000,
			                     	"minOrderAmount": 10000
			                     }
			                     """;

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/coupons")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[POST /api-admin/v1/coupons] name 101자 초과 -> 400 Bad Request. INVALID_COUPON_NAME")
		void createCouponTemplateFailNameTooLong() throws Exception {
			// Arrange
			LocalDateTime expiredAt = LocalDateTime.now().plusDays(30);
			String longName = "가".repeat(101);
			AdminCreateCouponTemplateRequest request = new AdminCreateCouponTemplateRequest(
				longName, CouponType.FIXED, new BigDecimal("5000"),
				new BigDecimal("10000"), null, expiredAt
			);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/coupons")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_COUPON_NAME.getCode()));
		}


		@Test
		@DisplayName("[POST /api-admin/v1/coupons] FIXED 타입, value > minOrderAmount -> 400 Bad Request. COUPON_VALUE_EXCEEDS_MIN_ORDER_AMOUNT")
		void createCouponTemplateFailFixedValueExceedsMinOrderAmount() throws Exception {
			// Arrange
			LocalDateTime expiredAt = LocalDateTime.now().plusDays(30);
			AdminCreateCouponTemplateRequest request = new AdminCreateCouponTemplateRequest(
				"할인 쿠폰", CouponType.FIXED, new BigDecimal("15000"),
				new BigDecimal("10000"), null, expiredAt
			);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/coupons")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.COUPON_VALUE_EXCEEDS_MIN_ORDER_AMOUNT.getCode()));
		}


		@Test
		@DisplayName("[POST /api-admin/v1/coupons] RATE 타입, value=101 -> 400 Bad Request. INVALID_COUPON_VALUE")
		void createCouponTemplateFailRateValueExceeds100() throws Exception {
			// Arrange
			LocalDateTime expiredAt = LocalDateTime.now().plusDays(30);
			AdminCreateCouponTemplateRequest request = new AdminCreateCouponTemplateRequest(
				"할인 쿠폰", CouponType.RATE, new BigDecimal("101"),
				null, null, expiredAt
			);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/coupons")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_COUPON_VALUE.getCode()));
		}


		@Test
		@DisplayName("[POST /api-admin/v1/coupons] 과거 만료일 -> 400 Bad Request. INVALID_COUPON_EXPIRED_AT")
		void createCouponTemplateFailExpiredAtInPast() throws Exception {
			// Arrange
			LocalDateTime pastExpiredAt = LocalDateTime.now().minusDays(1);
			AdminCreateCouponTemplateRequest request = new AdminCreateCouponTemplateRequest(
				"할인 쿠폰", CouponType.FIXED, new BigDecimal("5000"),
				new BigDecimal("10000"), null, pastExpiredAt
			);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/coupons")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_COUPON_EXPIRED_AT.getCode()));
		}

	}


	@Nested
	@DisplayName("GET /api-admin/v1/coupons - 쿠폰 템플릿 목록 조회")
	class GetCouponTemplatesTest {

		@Test
		@DisplayName("[GET /api-admin/v1/coupons] 쿠폰 템플릿 2개 -> 200 OK. content에 2개 포함")
		void getCouponTemplatesList() throws Exception {
			// Arrange
			createCouponTemplate("5000원 할인", CouponType.FIXED, new BigDecimal("5000"),
				new BigDecimal("10000"), LocalDateTime.now().plusDays(30));
			createCouponTemplate("10% 할인", CouponType.RATE, new BigDecimal("10"),
				null, LocalDateTime.now().plusDays(30));

			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/coupons")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.totalElements").value(2));
		}


		@Test
		@DisplayName("[GET /api-admin/v1/coupons] 페이지네이션 page=0, size=1 -> 200 OK. 1개 반환, totalElements=2")
		void getCouponTemplatesWithPagination() throws Exception {
			// Arrange
			createCouponTemplate("5000원 할인", CouponType.FIXED, new BigDecimal("5000"),
				new BigDecimal("10000"), LocalDateTime.now().plusDays(30));
			createCouponTemplate("10% 할인", CouponType.RATE, new BigDecimal("10"),
				null, LocalDateTime.now().plusDays(30));

			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/coupons")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.param("page", "0")
					.param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.totalElements").value(2));
		}


		@Test
		@DisplayName("[GET /api-admin/v1/coupons] 쿠폰 템플릿 없음 -> 200 OK. content에 0개 포함")
		void getCouponTemplatesEmpty() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/coupons")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.content", hasSize(0)))
				.andExpect(jsonPath("$.totalElements").value(0));
		}


		@Test
		@DisplayName("[GET /api-admin/v1/coupons] LDAP 헤더 누락 -> 401 Unauthorized")
		void getCouponTemplatesUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/coupons"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}

	}


	@Nested
	@DisplayName("PUT /api-admin/v1/coupons/{couponId} - 쿠폰 템플릿 수정")
	class UpdateCouponTemplateTest {

		@Test
		@DisplayName("[PUT /api-admin/v1/coupons/{couponId}] 유효한 수정 요청 -> 200 OK. 수정된 name, expiredAt 반환")
		void updateCouponTemplateSuccess() throws Exception {
			// Arrange
			Long couponId = createCouponTemplateAndGetId("원래 쿠폰", CouponType.FIXED,
				new BigDecimal("5000"), new BigDecimal("10000"), LocalDateTime.now().plusDays(30));

			LocalDateTime newExpiredAt = LocalDateTime.now().plusDays(60);
			AdminUpdateCouponTemplateRequest request = new AdminUpdateCouponTemplateRequest(
				"수정된 쿠폰", newExpiredAt
			);

			// Act & Assert
			mockMvc.perform(put("/api-admin/v1/coupons/{couponId}", couponId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(couponId))
				.andExpect(jsonPath("$.name").value("수정된 쿠폰"))
				.andExpect(jsonPath("$.expiredAt").exists());
		}


		@Test
		@DisplayName("[PUT /api-admin/v1/coupons/{couponId}] 존재하지 않는 ID -> 404 Not Found. COUPON_TEMPLATE_NOT_FOUND")
		void updateCouponTemplateNotFound() throws Exception {
			// Arrange
			LocalDateTime newExpiredAt = LocalDateTime.now().plusDays(60);
			AdminUpdateCouponTemplateRequest request = new AdminUpdateCouponTemplateRequest(
				"수정된 쿠폰", newExpiredAt
			);

			// Act & Assert
			mockMvc.perform(put("/api-admin/v1/coupons/{couponId}", 999L)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.COUPON_TEMPLATE_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[PUT /api-admin/v1/coupons/{couponId}] 삭제된 템플릿 수정 -> 404 Not Found. COUPON_TEMPLATE_NOT_FOUND")
		void updateCouponTemplateDeleted() throws Exception {
			// Arrange
			Long couponId = createCouponTemplateAndGetId("삭제할 쿠폰", CouponType.FIXED,
				new BigDecimal("5000"), new BigDecimal("10000"), LocalDateTime.now().plusDays(30));
			deleteCouponTemplate(couponId);

			LocalDateTime newExpiredAt = LocalDateTime.now().plusDays(60);
			AdminUpdateCouponTemplateRequest request = new AdminUpdateCouponTemplateRequest(
				"수정된 쿠폰", newExpiredAt
			);

			// Act & Assert
			mockMvc.perform(put("/api-admin/v1/coupons/{couponId}", couponId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.COUPON_TEMPLATE_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[PUT /api-admin/v1/coupons/{couponId}] LDAP 헤더 누락 -> 401 Unauthorized")
		void updateCouponTemplateUnauthorized() throws Exception {
			// Arrange
			LocalDateTime newExpiredAt = LocalDateTime.now().plusDays(60);
			AdminUpdateCouponTemplateRequest request = new AdminUpdateCouponTemplateRequest(
				"수정된 쿠폰", newExpiredAt
			);

			// Act & Assert
			mockMvc.perform(put("/api-admin/v1/coupons/{couponId}", 1L)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}

	}


	@Nested
	@DisplayName("DELETE /api-admin/v1/coupons/{couponId} - 쿠폰 템플릿 삭제")
	class DeleteCouponTemplateTest {

		@Test
		@DisplayName("[DELETE /api-admin/v1/coupons/{couponId}] 유효한 삭제 요청 -> 204 No Content")
		void deleteCouponTemplateSuccess() throws Exception {
			// Arrange
			Long couponId = createCouponTemplateAndGetId("삭제할 쿠폰", CouponType.FIXED,
				new BigDecimal("5000"), new BigDecimal("10000"), LocalDateTime.now().plusDays(30));

			// Act & Assert
			mockMvc.perform(delete("/api-admin/v1/coupons/{couponId}", couponId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isNoContent());
		}


		@Test
		@DisplayName("[DELETE /api-admin/v1/coupons/{couponId}] 존재하지 않는 ID -> 404 Not Found. COUPON_TEMPLATE_NOT_FOUND")
		void deleteCouponTemplateNotFound() throws Exception {
			// Act & Assert
			mockMvc.perform(delete("/api-admin/v1/coupons/{couponId}", 999L)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.COUPON_TEMPLATE_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[DELETE /api-admin/v1/coupons/{couponId}] 이미 삭제된 템플릿 -> 404 Not Found. COUPON_TEMPLATE_NOT_FOUND")
		void deleteCouponTemplateAlreadyDeleted() throws Exception {
			// Arrange
			Long couponId = createCouponTemplateAndGetId("삭제할 쿠폰", CouponType.FIXED,
				new BigDecimal("5000"), new BigDecimal("10000"), LocalDateTime.now().plusDays(30));
			deleteCouponTemplate(couponId);

			// Act & Assert
			mockMvc.perform(delete("/api-admin/v1/coupons/{couponId}", couponId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.COUPON_TEMPLATE_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[DELETE /api-admin/v1/coupons/{couponId}] LDAP 헤더 누락 -> 401 Unauthorized")
		void deleteCouponTemplateUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(delete("/api-admin/v1/coupons/{couponId}", 1L))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}

	}


	@Nested
	@DisplayName("GET /api-admin/v1/coupons/{couponId}/issues - 쿠폰 발급 내역 조회")
	class GetIssuedCouponsTest {

		@Test
		@DisplayName("[GET /api-admin/v1/coupons/{couponId}/issues] 쿠폰 발급 후 조회 -> 200 OK. 1개 발급 내역 포함")
		void getIssuedCouponsSuccess() throws Exception {
			// Arrange
			Long couponId = createCouponTemplateAndGetId("테스트 쿠폰", CouponType.FIXED,
				new BigDecimal("5000"), new BigDecimal("10000"), LocalDateTime.now().plusDays(30));
			signUpUser(TEST_LOGIN_ID, TEST_PASSWORD, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
			issueCoupon(TEST_LOGIN_ID, TEST_PASSWORD, couponId);

			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/coupons/{couponId}/issues", couponId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").isNumber())
				.andExpect(jsonPath("$[0].couponTemplateId").value(couponId))
				.andExpect(jsonPath("$[0].userId").isNumber())
				.andExpect(jsonPath("$[0].status").value("AVAILABLE"))
				.andExpect(jsonPath("$[0].createdAt").exists());
		}


		@Test
		@DisplayName("[GET /api-admin/v1/coupons/{couponId}/issues] 발급 내역 없음 -> 200 OK. 빈 목록 반환")
		void getIssuedCouponsEmpty() throws Exception {
			// Arrange
			Long couponId = createCouponTemplateAndGetId("테스트 쿠폰", CouponType.FIXED,
				new BigDecimal("5000"), new BigDecimal("10000"), LocalDateTime.now().plusDays(30));

			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/coupons/{couponId}/issues", couponId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$", hasSize(0)));
		}


		@Test
		@DisplayName("[GET /api-admin/v1/coupons/{couponId}/issues] LDAP 헤더 누락 -> 401 Unauthorized")
		void getIssuedCouponsUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/coupons/{couponId}/issues", 1L))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}

	}


	/**
	 * 테스트 헬퍼 메서드
	 */

	// 쿠폰 템플릿 생성 후 ID 반환
	private Long createCouponTemplateAndGetId(String name, CouponType type, BigDecimal value,
		BigDecimal minOrderAmount, LocalDateTime expiredAt) throws Exception {
		AdminCreateCouponTemplateRequest request = new AdminCreateCouponTemplateRequest(
			name, type, value, minOrderAmount, null, expiredAt
		);
		MvcResult result = mockMvc.perform(post("/api-admin/v1/coupons")
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}


	// 쿠폰 템플릿 생성 (ID 불필요 시)
	private void createCouponTemplate(String name, CouponType type, BigDecimal value,
		BigDecimal minOrderAmount, LocalDateTime expiredAt) throws Exception {
		AdminCreateCouponTemplateRequest request = new AdminCreateCouponTemplateRequest(
			name, type, value, minOrderAmount, null, expiredAt
		);
		mockMvc.perform(post("/api-admin/v1/coupons")
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}


	// 쿠폰 템플릿 삭제
	private void deleteCouponTemplate(Long couponId) throws Exception {
		mockMvc.perform(delete("/api-admin/v1/coupons/{couponId}", couponId)
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
			.andExpect(status().isNoContent());
	}


	// 사용자 생성
	private void signUpUser(String loginId, String password, String name, LocalDate birthDate, String email) throws Exception {
		UserSignUpRequest request = new UserSignUpRequest(loginId, password, name, birthDate, email);
		mockMvc.perform(post("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}


	// 쿠폰 발급 후 발급된 쿠폰 ID 반환
	private Long issueCoupon(String loginId, String password, Long couponId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId)
				.header(USER_LOGIN_ID_HEADER, loginId)
				.header(USER_LOGIN_PW_HEADER, password))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("issuedCouponId").asLong();
	}

}
