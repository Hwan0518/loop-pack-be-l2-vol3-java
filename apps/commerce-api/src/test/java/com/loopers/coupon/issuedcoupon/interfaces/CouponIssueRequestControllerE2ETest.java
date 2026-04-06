package com.loopers.coupon.issuedcoupon.interfaces;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
import com.loopers.coupon.coupontemplate.interfaces.web.request.AdminCreateCouponTemplateRequest;
import com.loopers.coupon.issuedcoupon.infrastructure.entity.CouponIssueRequestEntity;
import com.loopers.coupon.issuedcoupon.infrastructure.jpa.CouponIssueRequestJpaRepository;
import com.loopers.support.common.error.ErrorType;
import com.loopers.support.common.outbox.infrastructure.entity.OutboxEventApiEntity;
import com.loopers.support.common.outbox.infrastructure.jpa.OutboxEventApiJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.user.user.interfaces.web.request.UserSignUpRequest;
import com.loopers.utils.DatabaseCleanUp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("CouponIssueRequestController E2E 테스트")
class CouponIssueRequestControllerE2ETest {

	private static final String ADMIN_LDAP_HEADER = "X-Loopers-Ldap";
	private static final String ADMIN_LDAP_VALUE = "loopers.admin";
	private static final String USER_LOGIN_ID_HEADER = "X-Loopers-LoginId";
	private static final String USER_LOGIN_PW_HEADER = "X-Loopers-LoginPw";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	@Autowired
	private CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

	@Autowired
	private OutboxEventApiJpaRepository outboxEventApiJpaRepository;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Test
	@DisplayName("[POST/GET /api/v1/coupon-issue-requests] 발급 요청 생성 후 polling 조회 -> 202 Accepted + 200 OK, 요청/Outbox 저장")
	void createIssueRequest_thenGetIssueRequest_persistsRequestAndOutbox() throws Exception {
		// Arrange
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		String loginId = "couponuser" + suffix;
		String password = "Test1234!";
		signUpUser(loginId, password, loginId + "@test.com");
		Long couponTemplateId = createCouponTemplateAndGetId("비동기 쿠폰 " + suffix);
		String requestId = "coupon-req-" + suffix;

		// Act
		mockMvc.perform(post("/api/v1/coupon-issue-requests")
				.header(USER_LOGIN_ID_HEADER, loginId)
				.header(USER_LOGIN_PW_HEADER, password)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
					put("couponTemplateId", couponTemplateId);
					put("requestId", requestId);
				}})))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.requestId").value(requestId))
			.andExpect(jsonPath("$.status").value("PENDING"));

		MvcResult getResult = mockMvc.perform(get("/api/v1/coupon-issue-requests/{requestId}", requestId)
				.header(USER_LOGIN_ID_HEADER, loginId)
				.header(USER_LOGIN_PW_HEADER, password))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.requestId").value(requestId))
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andReturn();

		// Assert
		CouponIssueRequestEntity savedRequest = couponIssueRequestJpaRepository.findByRequestId(requestId)
			.orElseThrow(() -> new IllegalStateException("coupon issue request not found"));
		OutboxEventApiEntity outboxEvent = outboxEventApiJpaRepository.findAll().stream()
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("outbox event not found"));
		JsonNode getJson = objectMapper.readTree(getResult.getResponse().getContentAsString());

		assertAll(
			() -> assertThat(savedRequest.getUserId()).isNotNull(),
			() -> assertThat(savedRequest.getCouponTemplateId()).isEqualTo(couponTemplateId),
			() -> assertThat(savedRequest.getStatus()).isEqualTo("PENDING"),
			() -> assertThat(outboxEvent.getAggregateType()).isEqualTo("COUPON"),
			() -> assertThat(outboxEvent.getAggregateId()).isEqualTo(String.valueOf(couponTemplateId)),
			() -> assertThat(outboxEvent.getEventType()).isEqualTo("COUPON_ISSUE_REQUESTED"),
			() -> assertThat(outboxEvent.getTopic()).isEqualTo("coupon-issue-requests"),
			() -> assertThat(outboxEvent.getPartitionKey()).isEqualTo(String.valueOf(couponTemplateId)),
			() -> assertThat(outboxEvent.getStatus()).isEqualTo("PENDING"),
			() -> assertThat(outboxEvent.getPayload()).contains(requestId),
			() -> assertThat(getJson.get("status").asText()).isEqualTo("PENDING")
		);
	}


	@Test
	@DisplayName("[POST /api/v1/coupon-issue-requests] 동일 userId + 동일 requestId 재요청 -> 기존 요청 반환, 중복 저장 없음")
	void createIssueRequest_sameRequestIdBySameUser_returnsExistingWithoutDuplicates() throws Exception {
		// Arrange
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		String loginId = "couponuser" + suffix;
		String password = "Test1234!";
		signUpUser(loginId, password, loginId + "@test.com");
		Long couponTemplateId = createCouponTemplateAndGetId("중복 방지 쿠폰 " + suffix);
		String requestId = "coupon-req-" + suffix;
		String payload = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
			put("couponTemplateId", couponTemplateId);
			put("requestId", requestId);
		}});

		// Act
		mockMvc.perform(post("/api/v1/coupon-issue-requests")
				.header(USER_LOGIN_ID_HEADER, loginId)
				.header(USER_LOGIN_PW_HEADER, password)
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.requestId").value(requestId))
			.andExpect(jsonPath("$.status").value("PENDING"));

		mockMvc.perform(post("/api/v1/coupon-issue-requests")
				.header(USER_LOGIN_ID_HEADER, loginId)
				.header(USER_LOGIN_PW_HEADER, password)
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.requestId").value(requestId))
			.andExpect(jsonPath("$.status").value("PENDING"));

		// Assert
		assertAll(
			() -> assertThat(couponIssueRequestJpaRepository.findAll()).hasSize(1),
			() -> assertThat(outboxEventApiJpaRepository.findAll()).hasSize(1)
		);
	}


	@Test
	@DisplayName("[GET /api/v1/coupon-issue-requests/{requestId}] 다른 사용자의 요청 조회 -> 404 COUPON_ISSUE_REQUEST_NOT_FOUND")
	void getIssueRequest_otherUser_returnsNotFound() throws Exception {
		// Arrange
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		String ownerLoginId = "couponowner" + suffix;
		String otherLoginId = "couponother" + suffix;
		String password = "Test1234!";
		signUpUser(ownerLoginId, password, ownerLoginId + "@test.com");
		signUpUser(otherLoginId, password, otherLoginId + "@test.com");
		Long couponTemplateId = createCouponTemplateAndGetId("조회 제한 쿠폰 " + suffix);
		String requestId = "coupon-req-" + suffix;

		mockMvc.perform(post("/api/v1/coupon-issue-requests")
				.header(USER_LOGIN_ID_HEADER, ownerLoginId)
				.header(USER_LOGIN_PW_HEADER, password)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
					put("couponTemplateId", couponTemplateId);
					put("requestId", requestId);
				}})))
			.andExpect(status().isAccepted());

		// Act & Assert
		mockMvc.perform(get("/api/v1/coupon-issue-requests/{requestId}", requestId)
				.header(USER_LOGIN_ID_HEADER, otherLoginId)
				.header(USER_LOGIN_PW_HEADER, password))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(ErrorType.COUPON_ISSUE_REQUEST_NOT_FOUND.getCode()));
	}


	private void signUpUser(String loginId, String password, String email) throws Exception {
		UserSignUpRequest request = new UserSignUpRequest(
			loginId,
			password,
			"쿠폰유저",
			LocalDate.of(1990, 1, 1),
			email
		);
		mockMvc.perform(post("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}


	private Long createCouponTemplateAndGetId(String name) throws Exception {
		AdminCreateCouponTemplateRequest request = new AdminCreateCouponTemplateRequest(
			name,
			CouponType.FIXED,
			new BigDecimal("5000"),
			new BigDecimal("10000"),
			null,
			LocalDateTime.now().plusDays(30)
		);

		MvcResult result = mockMvc.perform(post("/api-admin/v1/coupons")
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated())
			.andReturn();

		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}
}
