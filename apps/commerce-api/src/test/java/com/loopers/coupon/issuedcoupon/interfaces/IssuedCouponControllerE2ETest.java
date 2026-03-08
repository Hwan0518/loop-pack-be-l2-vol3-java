package com.loopers.coupon.issuedcoupon.interfaces;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
import com.loopers.coupon.coupontemplate.interfaces.web.request.AdminCreateCouponTemplateRequest;
import com.loopers.coupon.issuedcoupon.infrastructure.cache.CaffeineCouponIssueDuplicateGuard;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("IssuedCouponController E2E 테스트")
class IssuedCouponControllerE2ETest {

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

	// @BeforeEach에서 생성된 쿠폰 템플릿 ID
	private Long couponTemplateId;


	@BeforeEach
	void setUp() throws Exception {
		// 테스트 사용자 생성
		signUpUser(TEST_LOGIN_ID, TEST_PASSWORD, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");

		// 쿠폰 템플릿 생성 (관리자 API)
		couponTemplateId = createCouponTemplateAndGetId("테스트 5000원 할인 쿠폰", CouponType.FIXED,
			new BigDecimal("5000"), new BigDecimal("10000"), LocalDateTime.now().plusDays(30));
	}


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
	@DisplayName("POST /api/v1/coupons/{couponId}/issue - 쿠폰 발급")
	class IssueCouponTest {

		@Test
		@DisplayName("[POST /api/v1/coupons/{couponId}/issue] 유효한 발급 요청 -> 201 Created. issuedCouponId 포함")
		void issueCouponSuccess() throws Exception {
			// Act & Assert
			mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponTemplateId)
					.header(USER_LOGIN_ID_HEADER, TEST_LOGIN_ID)
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.issuedCouponId").isNumber());
		}


		@Test
		@DisplayName("[POST /api/v1/coupons/{couponId}/issue] 동일 쿠폰 중복 발급 -> 409 Conflict. COUPON_ISSUE_DUPLICATED")
		void issueCouponDuplicated() throws Exception {
			// Arrange — 1차 발급
			issueCoupon(TEST_LOGIN_ID, TEST_PASSWORD, couponTemplateId);

			// Act & Assert — 2차 동일 발급 시도
			mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponTemplateId)
					.header(USER_LOGIN_ID_HEADER, TEST_LOGIN_ID)
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(ErrorType.COUPON_ISSUE_DUPLICATED.getCode()));
		}


		@Test
		@DisplayName("[POST /api/v1/coupons/{couponId}/issue] 삭제된 쿠폰 템플릿 발급 시도 -> 404 Not Found. COUPON_TEMPLATE_NOT_FOUND")
		void issueCouponDeletedTemplate() throws Exception {
			// Arrange — 쿠폰 템플릿 삭제
			deleteCouponTemplate(couponTemplateId);

			// Act & Assert
			mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponTemplateId)
					.header(USER_LOGIN_ID_HEADER, TEST_LOGIN_ID)
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.COUPON_TEMPLATE_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[POST /api/v1/coupons/{couponId}/issue] 존재하지 않는 쿠폰 템플릿 발급 시도 -> 404 Not Found. COUPON_TEMPLATE_NOT_FOUND")
		void issueCouponTemplateNotFound() throws Exception {
			// Act & Assert
			mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", 999L)
					.header(USER_LOGIN_ID_HEADER, TEST_LOGIN_ID)
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.COUPON_TEMPLATE_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[POST /api/v1/coupons/{couponId}/issue] 인증 헤더 누락 -> 401 Unauthorized")
		void issueCouponUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponTemplateId))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}

	}


	@Nested
	@DisplayName("GET /api/v1/users/me/coupons - 내 쿠폰 목록 조회")
	class GetMyIssuedCouponsTest {

		@Test
		@DisplayName("[GET /api/v1/users/me/coupons] 쿠폰 1개 발급 후 조회 -> 200 OK. 1개 쿠폰, templateName, type, value, status=AVAILABLE 포함")
		void getMyIssuedCouponsSuccess() throws Exception {
			// Arrange — 쿠폰 발급
			issueCoupon(TEST_LOGIN_ID, TEST_PASSWORD, couponTemplateId);

			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me/coupons")
					.header(USER_LOGIN_ID_HEADER, TEST_LOGIN_ID)
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].issuedCouponId").isNumber())
				.andExpect(jsonPath("$[0].templateName").value("테스트 5000원 할인 쿠폰"))
				.andExpect(jsonPath("$[0].type").value("FIXED"))
				.andExpect(jsonPath("$[0].value").value(5000))
				.andExpect(jsonPath("$[0].status").value("AVAILABLE"));
		}


		@Test
		@DisplayName("[GET /api/v1/users/me/coupons] 쿠폰 없음 -> 200 OK. 빈 목록 반환")
		void getMyIssuedCouponsEmpty() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me/coupons")
					.header(USER_LOGIN_ID_HEADER, TEST_LOGIN_ID)
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$", hasSize(0)));
		}


		@Test
		@DisplayName("[GET /api/v1/users/me/coupons] 다른 사용자의 쿠폰은 조회 불가 -> 200 OK. 본인의 쿠폰만 반환")
		void getMyIssuedCouponsOtherUserNotVisible() throws Exception {
			// Arrange — 다른 사용자 생성 및 쿠폰 발급
			String otherLoginId = "otheruser01";
			String otherPassword = "Other1234!";
			signUpUser(otherLoginId, otherPassword, "김철수", LocalDate.of(1995, 5, 20), "other@example.com");
			issueCoupon(otherLoginId, otherPassword, couponTemplateId);

			// Act & Assert — 테스트 사용자로 조회 시 다른 사용자의 쿠폰은 보이지 않음
			mockMvc.perform(get("/api/v1/users/me/coupons")
					.header(USER_LOGIN_ID_HEADER, TEST_LOGIN_ID)
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$", hasSize(0)));
		}


		@Test
		@DisplayName("[GET /api/v1/users/me/coupons] 인증 헤더 누락 -> 401 Unauthorized")
		void getMyIssuedCouponsUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/users/me/coupons"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}

	}


	/**
	 * 테스트 헬퍼 메서드
	 */

	// 쿠폰 템플릿 생성 후 ID 반환 (관리자)
	private Long createCouponTemplateAndGetId(String name, CouponType type, BigDecimal value,
		BigDecimal minOrderAmount, LocalDateTime expiredAt) throws Exception {
		AdminCreateCouponTemplateRequest request = new AdminCreateCouponTemplateRequest(
			name, type, value, minOrderAmount, expiredAt
		);
		MvcResult result = mockMvc.perform(post("/api-admin/v1/coupons")
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}


	// 쿠폰 템플릿 삭제 (관리자)
	private void deleteCouponTemplate(Long couponId) throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.delete("/api-admin/v1/coupons/{couponId}", couponId)
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
