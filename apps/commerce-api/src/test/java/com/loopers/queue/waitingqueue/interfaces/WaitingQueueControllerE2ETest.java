package com.loopers.queue.waitingqueue.interfaces;


import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.queue.waitingqueue.application.port.out.WaitingQueueRedisPort;
import com.loopers.queue.waitingqueue.application.port.out.QueueAuthPort;
import com.loopers.support.common.error.ErrorType;
import com.loopers.testcontainers.KafkaTestContainersConfig;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.user.user.interfaces.web.request.UserSignUpRequest;
import com.loopers.utils.DatabaseCleanUp;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.loopers.config.redis.RedisConfig;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class, KafkaTestContainersConfig.class})
@DisplayName("WaitingQueueController E2E 테스트")
class WaitingQueueControllerE2ETest {

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
	private WaitingQueueRedisPort waitingQueueRedisPort;

	@Autowired
	private QueueAuthPort queueAuthPort;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
		// Redis 대기열 정리
		redisTemplate.delete("waiting-queue");
		redisTemplate.delete("waiting-queue-seq");
		// entry-token:* 키 정리
		var entryTokenKeys = redisTemplate.keys("entry-token:*");
		if (entryTokenKeys != null && !entryTokenKeys.isEmpty()) {
			redisTemplate.delete(entryTokenKeys);
		}
	}


	// helper — 회원가입
	private void signUpUser(String loginId, String password) throws Exception {
		UserSignUpRequest request = new UserSignUpRequest(
			loginId, password, "테스트유저", LocalDate.of(1990, 1, 1), "test@test.com"
		);
		mockMvc.perform(post("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}


	// helper — 대기열 토큰 발급
	private String issueQueueToken(String loginId, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/auth/queue-token")
				.header("X-Loopers-LoginId", loginId)
				.header("X-Loopers-LoginPw", password))
			.andExpect(status().isOk())
			.andReturn();

		return objectMapper.readTree(result.getResponse().getContentAsString())
			.get("queueToken").asText();
	}


	@Nested
	@DisplayName("POST /queue/enter - 대기열 진입")
	class EnterTest {

		@Test
		@DisplayName("[POST /queue/enter] 유효한 서명 토큰 -> 200 OK. status=ACCEPTED, retryAfterMs, refreshedToken 포함")
		void enter_validToken_returnsAccepted() throws Exception {
			// Arrange
			signUpUser("testuser01", "Test1234!");
			String queueToken = issueQueueToken("testuser01", "Test1234!");

			// Act & Assert
			mockMvc.perform(post("/queue/enter")
					.header("X-Queue-Token", queueToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACCEPTED"))
				.andExpect(jsonPath("$.retryAfterMs").value(greaterThanOrEqualTo(0)))
				.andExpect(jsonPath("$.refreshedToken").value(notNullValue()));
		}


		@Test
		@DisplayName("[POST /queue/enter] 서명 토큰 없음 -> 400 Bad Request. X-Queue-Token 헤더 필수")
		void enter_noToken_returns400() throws Exception {
			// Act & Assert
			mockMvc.perform(post("/queue/enter"))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[POST /queue/enter] 잘못된 서명 토큰 -> 401 Unauthorized. INVALID_QUEUE_TOKEN")
		void enter_invalidToken_returns401() throws Exception {
			// Act & Assert
			mockMvc.perform(post("/queue/enter")
					.header("X-Queue-Token", "invalid-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_QUEUE_TOKEN.getCode()));
		}
	}


	@Nested
	@DisplayName("GET /queue/position - 순번 조회")
	class PositionTest {

		@Test
		@DisplayName("[GET /queue/position] 대기열에 존재하는 사용자 순번 조회 -> 200 OK. position>=1, estimatedWaitSeconds, retryAfterMs 포함")
		void getPosition_inQueue_returnsPosition() throws Exception {
			// Arrange — Redis에 직접 사용자 추가 (Kafka consumer 타이밍 의존 제거)
			signUpUser("testuser01", "Test1234!");
			String queueToken = issueQueueToken("testuser01", "Test1234!");

			// userId 추출 후 Redis 대기열에 직접 적재
			Long userId = queueAuthPort.resolveUserId(queueToken);
			waitingQueueRedisPort.addToQueue(userId);

			// Act & Assert
			mockMvc.perform(get("/queue/position")
					.header("X-Queue-Token", queueToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.position").value(greaterThanOrEqualTo(1)))
				.andExpect(jsonPath("$.estimatedWaitSeconds").isNumber())
				.andExpect(jsonPath("$.retryAfterMs").value(greaterThanOrEqualTo(0)))
				.andExpect(jsonPath("$.refreshedToken").value(notNullValue()));
		}


		@Test
		@DisplayName("[GET /queue/position] 미진입 상태에서 순번 조회 -> 404 QUEUE_NOT_ENTERED. 대기열에 없고 입장 토큰도 없음")
		void getPosition_notEntered_returns404() throws Exception {
			// Arrange
			signUpUser("testuser01", "Test1234!");
			String queueToken = issueQueueToken("testuser01", "Test1234!");

			// Act & Assert (진입하지 않고 순번 조회)
			mockMvc.perform(get("/queue/position")
					.header("X-Queue-Token", queueToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.QUEUE_NOT_ENTERED.getCode()));
		}


		@Test
		@DisplayName("[GET /queue/position] 서명 토큰 없음 -> 400 Bad Request")
		void getPosition_noToken_returns400() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/queue/position"))
				.andExpect(status().isBadRequest());
		}
	}
}
