package com.loopers.ranking.infrastructure.redis;


import com.loopers.ranking.application.port.out.CounterResult;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;


@SpringBootTest(properties = {
	"spring.kafka.listener.auto-startup=false",
	"spring.kafka.bootstrap-servers=localhost:19092"
})
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("RankingRedisAdapter 통합 테스트")
class RankingRedisAdapterTest {

	@Autowired
	private RankingRedisAdapter rankingRedisAdapter;

	@Autowired
	private RedisTemplate<String, String> redisTemplate;

	@Autowired
	private RedisCleanUp redisCleanUp;


	@AfterEach
	void tearDown() {
		redisCleanUp.truncateAll();
	}


	@Nested
	@DisplayName("incrementCounterAndGetCounts() 테스트")
	class IncrementCounterTest {

		@Test
		@DisplayName("[incrementCounterAndGetCounts()] 첫 카운터 갱신 -> old=0, new=delta")
		void firstIncrement() {
			// Act
			CounterResult result = rankingRedisAdapter.incrementCounterAndGetCounts(
				"20260408", 1L, 5, 2, 1);

			// Assert
			assertThat(result.oldView()).isEqualTo(0);
			assertThat(result.newView()).isEqualTo(5);
			assertThat(result.oldLike()).isEqualTo(0);
			assertThat(result.newLike()).isEqualTo(2);
			assertThat(result.oldOrder()).isEqualTo(0);
			assertThat(result.newOrder()).isEqualTo(1);
		}

		@Test
		@DisplayName("[incrementCounterAndGetCounts()] 누적 카운터 갱신 -> old=이전값, new=이전값+delta")
		void accumulatedIncrement() {
			// Arrange — 첫 번째 호출
			rankingRedisAdapter.incrementCounterAndGetCounts("20260408", 1L, 10, 3, 2);

			// Act — 두 번째 호출
			CounterResult result = rankingRedisAdapter.incrementCounterAndGetCounts(
				"20260408", 1L, 5, 1, 0);

			// Assert
			assertThat(result.oldView()).isEqualTo(10);
			assertThat(result.newView()).isEqualTo(15);
			assertThat(result.oldLike()).isEqualTo(3);
			assertThat(result.newLike()).isEqualTo(4);
			assertThat(result.oldOrder()).isEqualTo(2);
			assertThat(result.newOrder()).isEqualTo(2); // delta=0이므로 변경 없음
		}

		@Test
		@DisplayName("[incrementCounterAndGetCounts()] delta=0인 타입 -> HGET으로 조회만 수행")
		void zeroDeltaReadsOnly() {
			// Arrange
			rankingRedisAdapter.incrementCounterAndGetCounts("20260408", 1L, 10, 0, 0);

			// Act — 모든 delta가 0
			CounterResult result = rankingRedisAdapter.incrementCounterAndGetCounts(
				"20260408", 1L, 0, 0, 0);

			// Assert
			assertThat(result.oldView()).isEqualTo(10);
			assertThat(result.newView()).isEqualTo(10);
			assertThat(result.oldLike()).isEqualTo(0);
			assertThat(result.newLike()).isEqualTo(0);
		}
	}


	@Nested
	@DisplayName("incrementScore() 테스트")
	class IncrementScoreTest {

		@Test
		@DisplayName("[incrementScore()] ZINCRBY -> ZSET에 점수 누적")
		void incrementScore() {
			// Act
			rankingRedisAdapter.incrementScore("20260408", 1L, 0.35);
			rankingRedisAdapter.incrementScore("20260408", 1L, 0.15);

			// Assert
			Double score = redisTemplate.opsForZSet().score("ranking:all:20260408", "1");
			assertThat(score).isNotNull();
			assertThat(score).isCloseTo(0.50, within(1e-10));
		}

		@Test
		@DisplayName("[incrementScore()] 다른 상품 -> 별도 멤버로 관리")
		void differentProducts() {
			// Act
			rankingRedisAdapter.incrementScore("20260408", 1L, 0.5);
			rankingRedisAdapter.incrementScore("20260408", 2L, 0.8);

			// Assert
			assertThat(redisTemplate.opsForZSet().score("ranking:all:20260408", "1"))
				.isCloseTo(0.5, within(1e-10));
			assertThat(redisTemplate.opsForZSet().score("ranking:all:20260408", "2"))
				.isCloseTo(0.8, within(1e-10));
		}
	}


	@Nested
	@DisplayName("fullScoreFlow 테스트")
	class FullScoreFlowTest {

		@Test
		@DisplayName("[incrementCounterAndGetCounts() + incrementScore()] 카운터 갱신 → saturation delta 계산 → ZSET 반영 flow 검증")
		void fullScoreFlow() {
			// Arrange & Act — view 5회, like 2회, order 1회 적재
			CounterResult counts = rankingRedisAdapter.incrementCounterAndGetCounts("20260409", 1L, 5, 2, 1);

			// saturation delta 계산 (service 로직 재현)
			double score = 0.15 * (5.0 / (5.0 + 100.0)) + 0.35 * (2.0 / (2.0 + 10.0)) + 0.50 * (1.0 / (1.0 + 3.0));
			rankingRedisAdapter.incrementScore("20260409", 1L, score);
			rankingRedisAdapter.ensureTtl("20260409", Duration.ofDays(2));

			// Assert — ZSET 점수 확인
			Double storedScore = redisTemplate.opsForZSet().score("ranking:all:20260409", "1");
			assertThat(storedScore).isNotNull();
			assertThat(storedScore).isCloseTo(score, within(1e-10));
		}
	}


	@Nested
	@DisplayName("ensureTtl() 테스트")
	class EnsureTtlTest {

		@Test
		@DisplayName("[ensureTtl()] 4개 키에 TTL 설정")
		void setsTtl() {
			// Arrange — 키 생성을 위해 데이터 적재
			rankingRedisAdapter.incrementCounterAndGetCounts("20260408", 1L, 1, 1, 1);
			rankingRedisAdapter.incrementScore("20260408", 1L, 0.5);

			// Act
			rankingRedisAdapter.ensureTtl("20260408", Duration.ofDays(2));

			// Assert
			assertThat(redisTemplate.getExpire("ranking:all:20260408")).isGreaterThan(0);
			assertThat(redisTemplate.getExpire("ranking:counter:view:20260408")).isGreaterThan(0);
			assertThat(redisTemplate.getExpire("ranking:counter:like:20260408")).isGreaterThan(0);
			assertThat(redisTemplate.getExpire("ranking:counter:order:20260408")).isGreaterThan(0);
		}
	}

}
