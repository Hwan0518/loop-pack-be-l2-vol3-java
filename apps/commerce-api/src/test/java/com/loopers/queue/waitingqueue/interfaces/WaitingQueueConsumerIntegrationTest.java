package com.loopers.queue.waitingqueue.interfaces;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.config.redis.RedisConfig;
import com.loopers.testcontainers.KafkaTestContainersConfig;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.user.user.interfaces.web.request.UserSignUpRequest;
import com.loopers.utils.DatabaseCleanUp;
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
 * 대기열 Consumer 통합 테스트
 * - Consumer ON / Scheduler OFF
 * - unique topic으로 residue 방지
 * - Kafka → Redis 적재 흐름을 실제 비동기 컴포넌트로 검증
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class, KafkaTestContainersConfig.class})
@TestPropertySource(properties = {
	"queue.waiting.consumer.enabled=true",
	"queue.waiting.scheduler.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("대기열 Consumer 통합 테스트")
class WaitingQueueConsumerIntegrationTest {

	// unique topic + group id: 클래스 로딩 시 한 번만 생성
	private static final String CONSUMER_IT_TOPIC =
		"waiting-queue-topic-consumer-" + UUID.randomUUID().toString().replace("-", "");
	private static final String CONSUMER_IT_GROUP_ID =
		"waiting-queue-consumer-it-" + UUID.randomUUID().toString().replace("-", "");

	@DynamicPropertySource
	static void overrideProps(DynamicPropertyRegistry registry) {
		registry.add("queue.waiting.topic", () -> CONSUMER_IT_TOPIC);
		registry.add("queue.waiting.consumer.group-id", () -> CONSUMER_IT_GROUP_ID);
	}


	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
	@Autowired
	private RedisTemplate<String, String> redisTemplate;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	@Autowired
	private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;


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
	@DisplayName("[Consumer 통합] POST /queue/enter 후 polling -> status=WAITING, position=1, totalWaiting=1. Consumer가 Kafka → Redis 적재 검증")
	void enter_thenPolling_eventuallyWaiting() throws Exception {
		// Arrange
		signUpUser("consumertest01", "Test1234!");
		String queueToken = issueQueueToken("consumertest01", "Test1234!");

		// Consumer partition assignment 대기
		waitForQueueConsumerAssignment();

		// Act — 대기열 진입
		MvcResult enterResult = mockMvc.perform(post("/queue/enter")
				.header("X-Queue-Token", queueToken))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode enterJson = objectMapper.readTree(enterResult.getResponse().getContentAsString());
		assertThat(enterJson.get("status").asText()).isEqualTo("ACCEPTED");
		String refreshedToken = enterJson.get("refreshedToken").asText();
		String enterReceipt = enterJson.get("enterReceipt").asText();

		// Polling — WAITING까지 대기 (10s deadline)
		long deadline = System.currentTimeMillis() + 10000;
		String finalStatus = null;
		long finalPosition = 0;
		long finalTotalWaiting = 0;

		while (System.currentTimeMillis() < deadline) {
			MvcResult positionResult = mockMvc.perform(get("/queue/position")
					.header("X-Queue-Token", refreshedToken)
					.header("X-Enter-Receipt", enterReceipt))
				.andReturn();

			if (positionResult.getResponse().getStatus() == 200) {
				JsonNode json = objectMapper.readTree(positionResult.getResponse().getContentAsString());
				String status = json.get("status").asText();
				refreshedToken = json.get("refreshedToken").asText();

				if ("WAITING".equals(status)) {
					finalStatus = status;
					finalPosition = json.get("position").asLong();
					finalTotalWaiting = json.get("totalWaiting").asLong();
					break;
				}
			}

			Thread.sleep(50);
		}

		// Assert — black-box 검증
		assertThat(finalStatus).isEqualTo("WAITING");
		assertThat(finalPosition).isEqualTo(1);
		assertThat(finalTotalWaiting).isEqualTo(1);
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

	private void signUpUser(String loginId, String password) throws Exception {
		UserSignUpRequest request = new UserSignUpRequest(
			loginId, password, "테스트유저", LocalDate.of(1990, 1, 1), loginId + "@test.com"
		);
		mockMvc.perform(post("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}

	private String issueQueueToken(String loginId, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/auth/queue-token")
				.header("X-Loopers-LoginId", loginId)
				.header("X-Loopers-LoginPw", password))
			.andExpect(status().isOk())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString())
			.get("queueToken").asText();
	}
}
