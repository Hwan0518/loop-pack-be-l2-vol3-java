package com.loopers.queue.waitingqueue.interfaces;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.config.redis.RedisConfig;
import com.loopers.support.common.error.ErrorType;
import com.loopers.testcontainers.KafkaTestContainersConfig;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.user.user.interfaces.web.request.UserSignUpRequest;
import com.loopers.utils.DatabaseCleanUp;
import java.time.LocalDate;
import java.util.ArrayList;
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


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class, KafkaTestContainersConfig.class})
@TestPropertySource(properties = {
	"queue.waiting.consumer.enabled=true",
	"queue.waiting.scheduler.enabled=false",
	"queue.waiting.max-capacity=3"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("대기열 처리량 초과 통합 테스트")
class WaitingQueueCapacityIntegrationTest {

	private static final String CAPACITY_IT_TOPIC =
		"waiting-queue-topic-capacity-" + UUID.randomUUID().toString().replace("-", "");
	private static final String CAPACITY_IT_GROUP_ID =
		"waiting-queue-consumer-capacity-" + UUID.randomUUID().toString().replace("-", "");

	@DynamicPropertySource
	static void overrideProps(DynamicPropertyRegistry registry) {
		registry.add("queue.waiting.topic", () -> CAPACITY_IT_TOPIC);
		registry.add("queue.waiting.consumer.group-id", () -> CAPACITY_IT_GROUP_ID);
	}


	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	@Autowired
	@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
	private RedisTemplate<String, String> redisTemplate;

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


	@Test
	@DisplayName("[Capacity 통합] max-capacity=3 에서 4명 진입 -> 3명 WAITING, 1명 QUEUE_CAPACITY_EXCEEDED")
	void enterOverCapacity_thenOneUserIsRejected() throws Exception {
		// Arrange
		waitForQueueConsumerAssignment();
		List<QueueSession> sessions = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
			String loginId = "capacity" + i + suffix;
			String password = "Test1234!";
			signUpUser(loginId, password, loginId + "@test.com");
			String queueToken = issueQueueToken(loginId, password);
			sessions.add(enterQueue(queueToken));
		}

		// Act
		long waitingCount = 0;
		long rejectedCount = 0;
		for (QueueSession session : sessions) {
			TerminalStatus terminalStatus = waitForTerminalStatus(session.queueToken(), session.enterReceipt());
			if (terminalStatus == TerminalStatus.WAITING) {
				waitingCount++;
			}
			if (terminalStatus == TerminalStatus.REJECTED) {
				rejectedCount++;
			}
		}

		// Assert
		assertThat(waitingCount).isEqualTo(3);
		assertThat(rejectedCount).isEqualTo(1);
	}


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


	private QueueSession enterQueue(String queueToken) throws Exception {
		MvcResult enterResult = mockMvc.perform(post("/queue/enter")
				.header("X-Queue-Token", queueToken))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode enterJson = objectMapper.readTree(enterResult.getResponse().getContentAsString());
		return new QueueSession(
			enterJson.get("refreshedToken").asText(),
			enterJson.get("enterReceipt").asText()
		);
	}


	private TerminalStatus waitForTerminalStatus(String queueToken, String enterReceipt) throws Exception {
		long deadline = System.currentTimeMillis() + 10000;
		String currentToken = queueToken;

		while (System.currentTimeMillis() < deadline) {
			MvcResult result = mockMvc.perform(get("/queue/position")
					.header("X-Queue-Token", currentToken)
					.header("X-Enter-Receipt", enterReceipt))
				.andReturn();

			int httpStatus = result.getResponse().getStatus();
			JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());

			if (httpStatus == 200) {
				String status = json.get("status").asText();
				currentToken = json.get("refreshedToken").asText();
				if ("WAITING".equals(status)) {
					return TerminalStatus.WAITING;
				}
			}

			if (httpStatus == 503
				&& ErrorType.QUEUE_CAPACITY_EXCEEDED.getCode().equals(json.get("code").asText())) {
				return TerminalStatus.REJECTED;
			}

			Thread.sleep(50);
		}

		fail("Queue terminal status timed out");
		return null;
	}


	private void signUpUser(String loginId, String password, String email) throws Exception {
		UserSignUpRequest request = new UserSignUpRequest(
			loginId,
			password,
			"처리량유저",
			LocalDate.of(1990, 1, 1),
			email
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


	private void cleanRedis() {
		for (String pattern : List.of("waiting-queue*", "entry-token:*", "queue-rejected:*", "order-lock:*")) {
			Set<String> keys = redisTemplate.keys(pattern);
			if (keys != null && !keys.isEmpty()) {
				redisTemplate.delete(keys);
			}
		}
	}


	private record QueueSession(String queueToken, String enterReceipt) {}

	private enum TerminalStatus {
		WAITING,
		REJECTED
	}
}
