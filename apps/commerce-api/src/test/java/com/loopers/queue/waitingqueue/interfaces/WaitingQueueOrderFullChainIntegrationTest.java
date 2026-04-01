package com.loopers.queue.waitingqueue.interfaces;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.config.redis.RedisConfig;
import com.loopers.ordering.order.interfaces.web.request.OrderCreateRequest;
import com.loopers.testcontainers.KafkaTestContainersConfig;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.user.user.interfaces.web.request.UserSignUpRequest;
import com.loopers.utils.DatabaseCleanUp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;


/**
 * 대기열 → 주문 full-chain 통합 테스트
 * - consumer/scheduler 모두 활성화
 * - unique topic으로 residue 방지
 * - 최종 TOKEN_ISSUED + 주문 성공만 검증 (중간 상태 강제 없음)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class, KafkaTestContainersConfig.class})
@TestPropertySource(properties = {
	"queue.waiting.consumer.enabled=true",
	"queue.waiting.scheduler.enabled=true"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("대기열 -> 주문 full-chain 통합 테스트")
class WaitingQueueOrderFullChainIntegrationTest {

	// topic + group id: 클래스 로딩 시 한 번만 생성, context 안에서 고정
	private static final String FULL_CHAIN_TOPIC =
		"waiting-queue-topic-fullchain-" + UUID.randomUUID().toString().replace("-", "");
	private static final String FULL_CHAIN_GROUP_ID =
		"waiting-queue-consumer-fullchain-" + UUID.randomUUID().toString().replace("-", "");

	@DynamicPropertySource
	static void overrideProperties(DynamicPropertyRegistry registry) {
		registry.add("queue.waiting.topic", () -> FULL_CHAIN_TOPIC);
		registry.add("queue.waiting.consumer.group-id", () -> FULL_CHAIN_GROUP_ID);
	}


	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
	@Autowired
	private RedisTemplate<String, String> redisTemplate;

	@Autowired
	private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

	private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
	private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
	private static final String ADMIN_LDAP_HEADER = "X-Loopers-Ldap";
	private static final String ADMIN_LDAP_VALUE = "loopers.admin";


	@BeforeEach
	void setUp() {
		cleanRedis();
	}

	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
		cleanRedis();
	}

	private void cleanRedis() {
		for (String pattern : List.of("waiting-queue*", "entry-token:*", "queue-rejected:*", "order-lock:*")) {
			Set<String> keys = redisTemplate.keys(pattern);
			if (keys != null && !keys.isEmpty()) {
				redisTemplate.delete(keys);
			}
		}
	}


	@Test
	@DisplayName("[Full-chain] 회원가입 → 토큰발급 → enter → position polling → TOKEN_ISSUED → 주문 성공")
	void issueQueueToken_enter_waitUntilTokenIssued_thenCreateOrder_success() throws Exception {
		// Arrange — unique test data
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		String loginId = "chain" + suffix;
		String password = "Test1234!";
		String email = loginId + "@test.com";
		String requestId = "chain-req-" + UUID.randomUUID();

		// 1. 회원가입
		signUpUser(loginId, password, "체인유저", LocalDate.of(1995, 5, 15), email);

		// 2. 브랜드 + 상품 + 장바구니
		Long brandId = createBrandAndGetId("브랜드" + suffix, "테스트");
		Long productId = createProductAndGetId(brandId, "상품" + suffix, new BigDecimal("10000"), 100L, "테스트 상품");
		Long cartItemId = addCartItemAndGetId(loginId, password, productId, 1L);

		// 3. 대기열 토큰 발급
		String queueToken = issueQueueToken(loginId, password);

		// 4. Consumer partition assignment 대기
		waitForQueueConsumerAssignment();

		// 5. 대기열 진입
		MvcResult enterResult = mockMvc.perform(post("/queue/enter")
				.header("X-Queue-Token", queueToken))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode enterJson = objectMapper.readTree(enterResult.getResponse().getContentAsString());
		String refreshedToken = enterJson.get("refreshedToken").asText();
		String enterReceipt = enterJson.get("enterReceipt").asText();

		// 6. TOKEN_ISSUED까지 polling
		String entryToken = waitUntilEntryTokenIssued(refreshedToken, enterReceipt);

		// 7. 주문 생성
		mockMvc.perform(post("/api/v1/orders")
				.header(LOGIN_ID_HEADER, loginId)
				.header(LOGIN_PW_HEADER, password)
				.header("X-Entry-Token", entryToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
					new OrderCreateRequest(List.of(cartItemId), requestId, null)
				)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.userId").isNumber())
			.andExpect(jsonPath("$.items").isArray());
	}


	// --- helpers ---

	private void waitForQueueConsumerAssignment() throws InterruptedException {
		MessageListenerContainer container =
			kafkaListenerEndpointRegistry.getListenerContainer("waitingQueueConsumerListener");

		assertThat(container).as("waitingQueueConsumerListener container").isNotNull();

		long deadline = System.currentTimeMillis() + 15000;
		while (System.currentTimeMillis() < deadline) {
			if (container.isRunning()
				&& container.getAssignedPartitions() != null
				&& !container.getAssignedPartitions().isEmpty()) {
				return;
			}
			Thread.sleep(50);
		}

		fail("waitingQueueConsumerListener partition assignment timed out");
	}


	private String waitUntilEntryTokenIssued(String queueToken, String enterReceipt) throws Exception {
		long deadline = System.currentTimeMillis() + 20000;
		String currentToken = queueToken;

		while (System.currentTimeMillis() < deadline) {
			MvcResult result = mockMvc.perform(get("/queue/position")
					.header("X-Queue-Token", currentToken)
					.header("X-Enter-Receipt", enterReceipt))
				.andExpect(status().isOk())
				.andReturn();

			JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
			String status = json.get("status").asText();
			currentToken = json.get("refreshedToken").asText();

			if ("TOKEN_ISSUED".equals(status)) {
				return json.get("entryToken").asText();
			}

			// PROCESSING, WAITING 모두 정상 중간 상태
			Thread.sleep(50);
		}

		fail("Entry token issuance timed out");
		return null;
	}


	private void signUpUser(String loginId, String password, String name, LocalDate birthDate, String email) throws Exception {
		UserSignUpRequest request = new UserSignUpRequest(loginId, password, name, birthDate, email);
		mockMvc.perform(post("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}


	private String issueQueueToken(String loginId, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/auth/queue-token")
				.header(LOGIN_ID_HEADER, loginId)
				.header(LOGIN_PW_HEADER, password))
			.andExpect(status().isOk())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("queueToken").asText();
	}


	private Long createBrandAndGetId(String name, String description) throws Exception {
		MvcResult result = mockMvc.perform(post("/api-admin/v1/brands")
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
					new java.util.LinkedHashMap<>() {{
						put("name", name);
						put("description", description);
					}})))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}


	private Long createProductAndGetId(Long brandId, String name, BigDecimal price, Long stock, String description) throws Exception {
		MvcResult result = mockMvc.perform(post("/api-admin/v1/products")
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
					new java.util.LinkedHashMap<>() {{
						put("brandId", brandId);
						put("name", name);
						put("price", price);
						put("stock", stock);
						put("description", description);
					}})))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}


	private Long addCartItemAndGetId(String loginId, String password, Long productId, Long quantity) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/cart/items")
				.header(LOGIN_ID_HEADER, loginId)
				.header(LOGIN_PW_HEADER, password)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
					new java.util.LinkedHashMap<>() {{
						put("productId", productId);
						put("quantity", quantity);
					}})))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}
}
