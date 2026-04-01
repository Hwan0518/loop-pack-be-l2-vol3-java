package com.loopers.queue.waitingqueue.interfaces;


import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.config.redis.RedisConfig;
import com.loopers.queue.waitingqueue.application.port.out.QueueAuthPort;
import com.loopers.queue.waitingqueue.application.port.out.WaitingQueueRedisPort;
import com.loopers.testcontainers.KafkaTestContainersConfig;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import java.util.Set;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;


/**
 * 대기열 Scheduler 통합 테스트
 * - Consumer OFF / Scheduler ON
 * - Redis 큐에 직접 적재 후 Scheduler가 입장 토큰을 발급하는 흐름 검증
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class, KafkaTestContainersConfig.class})
@TestPropertySource(properties = {
	"queue.waiting.consumer.enabled=false",
	"queue.waiting.scheduler.enabled=true"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("대기열 Scheduler 통합 테스트")
class WaitingQueueSchedulerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private QueueAuthPort queueAuthPort;

	@Autowired
	private WaitingQueueRedisPort waitingQueueRedisPort;

	@Autowired
	@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
	private RedisTemplate<String, String> redisTemplate;


	@BeforeEach
	void setUp() {
		cleanRedis();
	}


	@AfterEach
	void tearDown() {
		cleanRedis();
	}


	// Redis 대기열 관련 키 전체 정리
	private void cleanRedis() {
		deleteKeysByPattern("waiting-queue*");
		deleteKeysByPattern("entry-token:*");
		deleteKeysByPattern("queue-rejected:*");
		deleteKeysByPattern("order-lock:*");
	}


	private void deleteKeysByPattern(String pattern) {
		Set<String> keys = redisTemplate.keys(pattern);
		if (keys != null && !keys.isEmpty()) {
			redisTemplate.delete(keys);
		}
	}


	@Test
	@DisplayName("[Scheduler 통합] Redis 큐에 직접 적재 후 GET /queue/position polling -> 최종 TOKEN_ISSUED. Scheduler가 토큰 발급 검증")
	void directRedisEntry_thenPolling_eventuallyTokenIssued() throws Exception {
		// Arrange -- 임의 userId로 서명 토큰 생성 + Redis 대기열에 직접 적재
		Long userId = 999L;
		String queueToken = queueAuthPort.generateToken(userId);
		waitingQueueRedisPort.addToQueue(userId);

		// Polling -- Scheduler가 ZPOPMIN + SET EX로 입장 토큰을 발급할 때까지 대기
		long deadline = System.currentTimeMillis() + 5000;
		String finalStatus = null;
		String entryToken = null;

		while (System.currentTimeMillis() < deadline) {
			MvcResult positionResult = mockMvc.perform(get("/queue/position")
					.header("X-Queue-Token", queueToken))
				.andReturn();

			int httpStatus = positionResult.getResponse().getStatus();

			// 200 OK인 경우 상태 확인
			if (httpStatus == 200) {
				JsonNode positionJson = objectMapper.readTree(positionResult.getResponse().getContentAsString());
				String status = positionJson.get("status").asText();

				// TOKEN_ISSUED: Scheduler가 입장 토큰 발급 완료
				if ("TOKEN_ISSUED".equals(status)) {
					finalStatus = status;
					entryToken = positionJson.get("entryToken").asText();
					break;
				}

				// WAITING: 아직 Scheduler가 처리하지 않음 (정상 중간 상태)
				if ("WAITING".equals(status)) {
					// 갱신된 토큰으로 다음 요청에 사용
					queueToken = positionJson.get("refreshedToken").asText();
				}
			}

			Thread.sleep(50);
		}

		// Assert -- Scheduler가 입장 토큰 발급 완료, TOKEN_ISSUED 상태
		assertThat(finalStatus).isEqualTo("TOKEN_ISSUED");
		assertThat(entryToken).isNotNull();
		assertThat(entryToken).isNotBlank();
	}
}
