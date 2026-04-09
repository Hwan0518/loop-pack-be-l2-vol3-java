package com.loopers.ranking.application.service;


import com.loopers.ranking.application.dto.RankingDailyKey;
import com.loopers.ranking.application.port.out.*;
import com.loopers.ranking.infrastructure.scorer.SaturationScorer;
import com.loopers.support.idempotency.EventIdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("RankingScoreService 단위 테스트")
class RankingScoreServiceTest {

	@Mock
	private RankingDailyCounterCommandPort counterPort;

	@Mock
	private RankingDailyScoreCommandPort scorePort;

	@Mock
	private RankingProjectionDirtyPort dirtyPort;

	@Mock
	private RankingRedisPort rankingRedisPort;

	@Mock
	private EventIdempotencyService eventIdempotencyService;

	private RankingScorer scorer;
	private RankingScoreService rankingScoreService;


	@BeforeEach
	void setUp() {
		scorer = new SaturationScorer();
		rankingScoreService = new RankingScoreService(
			counterPort, scorePort, dirtyPort,
			rankingRedisPort, scorer, eventIdempotencyService
		);
	}


	@Nested
	@DisplayName("SaturationScorer 점수 계산 테스트")
	class ScorerTest {

		@Test
		@DisplayName("[calculateScore()] 모든 카운트 0 -> 0.0. 이벤트 없음")
		void allZero() {
			assertThat(scorer.calculateScore(0, 0, 0)).isEqualTo(0.0);
		}

		@Test
		@DisplayName("[calculateScore()] view=100, like=10, order=3 -> 모두 sat=0.5일 때 점수 0.5")
		void halfSaturation() {
			double score = scorer.calculateScore(100, 10, 3);
			assertThat(score).isCloseTo(0.5, within(1e-10));
		}

		@Test
		@DisplayName("[calculateScore()] 주문만 3건 -> 0.50*0.5 = 0.25. 주문 가중치가 가장 큼")
		void orderOnly() {
			double score = scorer.calculateScore(0, 0, 3);
			assertThat(score).isCloseTo(0.25, within(1e-10));
		}

		@Test
		@DisplayName("[calculateScore()] 주문 1건 > 좋아요 3건. 가중치 적용 의도대로 반영 (요구사항 검증)")
		void orderOneGreaterThanLikeThree() {
			double orderScore = scorer.calculateScore(0, 0, 1);
			double likeScore = scorer.calculateScore(0, 3, 0);
			assertThat(orderScore).isGreaterThan(likeScore);
		}
	}


	@Nested
	@DisplayName("persistDeltas() 테스트 — DB 단일 TX")
	class PersistDeltasTest {

		@Test
		@DisplayName("[persistDeltas()] 단일 상품 delta -> counter upsert + organic_score upsert + event_handled")
		void singleProductDelta() {
			// Arrange
			LocalDate statDate = LocalDate.of(2026, 4, 8);
			RankingDailyKey key = new RankingDailyKey(statDate, 1L);
			Map<RankingDailyKey, long[]> deltas = Map.of(key, new long[]{5, 2, 1});
			Set<String> eventIds = new LinkedHashSet<>(Set.of("evt-1", "evt-2"));

			// read-back 후 절대 카운터
			given(counterPort.getCounters(Set.of(key)))
				.willReturn(Map.of(key, new long[]{5, 2, 1}));

			// Act
			rankingScoreService.persistDeltas(deltas, eventIds);

			// Assert
			verify(counterPort).upsertDeltas(deltas);
			verify(scorePort).upsertOrganicScores(anyMap(), eq("SATURATION"));
			verify(eventIdempotencyService).markHandledBatch(eventIds, "ranking-collector");
		}

		@Test
		@DisplayName("[persistDeltas()] 다수 상품 -> 각 상품별 organic_score 계산")
		void multipleProducts() {
			// Arrange
			LocalDate statDate = LocalDate.of(2026, 4, 8);
			RankingDailyKey key1 = new RankingDailyKey(statDate, 1L);
			RankingDailyKey key2 = new RankingDailyKey(statDate, 2L);
			Map<RankingDailyKey, long[]> deltas = Map.of(
				key1, new long[]{10, 0, 0},
				key2, new long[]{0, 5, 0}
			);

			given(counterPort.getCounters(deltas.keySet()))
				.willReturn(Map.of(
					key1, new long[]{10, 0, 0},
					key2, new long[]{0, 5, 0}
				));

			// Act
			rankingScoreService.persistDeltas(deltas, new LinkedHashSet<>(Set.of("e1", "e2")));

			// Assert
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<RankingDailyKey, Double>> scoresCaptor = ArgumentCaptor.forClass(Map.class);
			verify(scorePort).upsertOrganicScores(scoresCaptor.capture(), eq("SATURATION"));
			Map<RankingDailyKey, Double> scores = scoresCaptor.getValue();
			assertThat(scores).hasSize(2);
			assertThat(scores.get(key1)).isGreaterThan(0.0);
			assertThat(scores.get(key2)).isGreaterThan(0.0);
		}

		@Test
		@DisplayName("[persistDeltas()] 음수 카운터 -> clamp 적용 후 0으로 scorer 입력")
		void negativeCounterClamp() {
			// Arrange — like_count 가 -3 인 상태 (unlike 중복)
			LocalDate statDate = LocalDate.of(2026, 4, 8);
			RankingDailyKey key = new RankingDailyKey(statDate, 1L);
			Map<RankingDailyKey, long[]> deltas = Map.of(key, new long[]{0, -3, 0});

			given(counterPort.getCounters(Set.of(key)))
				.willReturn(Map.of(key, new long[]{0, -3, 0}));

			// Act
			rankingScoreService.persistDeltas(deltas, new LinkedHashSet<>(Set.of("e1")));

			// Assert — organic_score = scorer(0, 0, 0) = 0.0
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<RankingDailyKey, Double>> scoresCaptor = ArgumentCaptor.forClass(Map.class);
			verify(scorePort).upsertOrganicScores(scoresCaptor.capture(), eq("SATURATION"));
			assertThat(scoresCaptor.getValue().get(key)).isCloseTo(0.0, within(1e-10));
		}
	}


	@Nested
	@DisplayName("reflectToRedis() 테스트 — Redis best-effort")
	class ReflectToRedisTest {

		@Test
		@DisplayName("[reflectToRedis()] 단일 상품 delta -> Redis HINCRBY + ZINCRBY + TTL")
		void singleProduct() {
			// Arrange
			LocalDate statDate = LocalDate.of(2026, 4, 8);
			RankingDailyKey key = new RankingDailyKey(statDate, 1L);
			Map<RankingDailyKey, long[]> deltas = Map.of(key, new long[]{5, 2, 1});

			given(rankingRedisPort.incrementCounterAndGetCounts(eq("20260408"), eq(1L), eq(5L), eq(2L), eq(1L)))
				.willReturn(new CounterResult(0, 5, 0, 2, 0, 1));

			// Act
			rankingScoreService.reflectToRedis(deltas);

			// Assert
			verify(rankingRedisPort).incrementScore(eq("20260408"), eq(1L), doubleThat(d -> d > 0));
			verify(rankingRedisPort).ensureTtl(eq("20260408"), eq(Duration.ofDays(2)));
		}

		@Test
		@DisplayName("[reflectToRedis()] scoreDelta=0 -> ZINCRBY 미호출")
		void zeroScoreDelta() {
			// Arrange
			LocalDate statDate = LocalDate.of(2026, 4, 8);
			RankingDailyKey key = new RankingDailyKey(statDate, 1L);
			Map<RankingDailyKey, long[]> deltas = Map.of(key, new long[]{0, 0, 0});

			given(rankingRedisPort.incrementCounterAndGetCounts(eq("20260408"), eq(1L), eq(0L), eq(0L), eq(0L)))
				.willReturn(new CounterResult(5, 5, 2, 2, 1, 1));

			// Act
			rankingScoreService.reflectToRedis(deltas);

			// Assert
			verify(rankingRedisPort, never()).incrementScore(anyString(), anyLong(), anyDouble());
		}

		@Test
		@DisplayName("[reflectToRedis()] 다른 날짜 키 -> 각 날짜별 TTL 보장")
		void multipleDates() {
			// Arrange
			LocalDate dateA = LocalDate.of(2026, 4, 8);
			LocalDate dateB = LocalDate.of(2026, 4, 9);
			Map<RankingDailyKey, long[]> deltas = Map.of(
				new RankingDailyKey(dateA, 1L), new long[]{1, 0, 0},
				new RankingDailyKey(dateB, 1L), new long[]{0, 1, 0}
			);

			given(rankingRedisPort.incrementCounterAndGetCounts(eq("20260408"), eq(1L), eq(1L), eq(0L), eq(0L)))
				.willReturn(new CounterResult(0, 1, 0, 0, 0, 0));
			given(rankingRedisPort.incrementCounterAndGetCounts(eq("20260409"), eq(1L), eq(0L), eq(1L), eq(0L)))
				.willReturn(new CounterResult(0, 0, 0, 1, 0, 0));

			// Act
			rankingScoreService.reflectToRedis(deltas);

			// Assert
			verify(rankingRedisPort).ensureTtl(eq("20260408"), eq(Duration.ofDays(2)));
			verify(rankingRedisPort).ensureTtl(eq("20260409"), eq(Duration.ofDays(2)));
		}
	}


	@Nested
	@DisplayName("markProjectionDirty() 테스트")
	class MarkDirtyTest {

		@Test
		@DisplayName("[markProjectionDirty()] 날짜 집합 -> dirtyPort.markDirty 호출")
		void marksCorrectDates() {
			// Arrange
			Set<LocalDate> dates = Set.of(LocalDate.of(2026, 4, 8));

			// Act
			rankingScoreService.markProjectionDirty(dates);

			// Assert
			verify(dirtyPort).markDirty(dates, "REDIS_WRITE_FAIL");
		}
	}


	@Nested
	@DisplayName("findAlreadyHandledIds() 테스트")
	class FindAlreadyHandledIdsTest {

		@Test
		@DisplayName("[findAlreadyHandledIds()] eventIdempotencyService에 위임")
		void delegatesToIdempotencyService() {
			Set<String> eventIds = Set.of("evt-1", "evt-2");
			given(eventIdempotencyService.findAlreadyHandledIds(eventIds, "ranking-collector"))
				.willReturn(Set.of("evt-1"));

			Set<String> result = rankingScoreService.findAlreadyHandledIds(eventIds);

			assertThat(result).containsExactly("evt-1");
		}
	}

}
