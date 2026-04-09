package com.loopers.ranking.application.service;


import com.loopers.ranking.application.port.out.CounterResult;
import com.loopers.ranking.application.port.out.RankingRedisPort;
import com.loopers.ranking.application.port.out.RankingScorer;
import com.loopers.ranking.infrastructure.scorer.SaturationScorer;
import com.loopers.support.idempotency.EventIdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("RankingScoreService 단위 테스트")
class RankingScoreServiceTest {

	@Mock
	private RankingRedisPort rankingRedisPort;

	@Mock
	private EventIdempotencyService eventIdempotencyService;

	private RankingScorer scorer;
	private RankingScoreService rankingScoreService;


	@BeforeEach
	void setUp() {
		scorer = new SaturationScorer();
		rankingScoreService = new RankingScoreService(rankingRedisPort, scorer, eventIdempotencyService);
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
			// 0.15*0.5 + 0.35*0.5 + 0.50*0.5 = 0.5
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
			// 주문 1건: 0.50 * sat(1, 3) = 0.50 * (1/4) = 0.125
			double orderScore = scorer.calculateScore(0, 0, 1);
			// 좋아요 3건: 0.35 * sat(3, 10) = 0.35 * (3/13) ≈ 0.0808
			double likeScore = scorer.calculateScore(0, 3, 0);

			// Assert — 주문 1건의 점수가 좋아요 3건보다 높아야 함
			assertThat(orderScore).isGreaterThan(likeScore);
		}
	}


	@Nested
	@DisplayName("applyDeltas() 테스트")
	class ApplyDeltasTest {

		@Test
		@DisplayName("[applyDeltas()] 단일 상품 delta -> Redis 카운터 갱신 + ZINCRBY + event_handled 기록")
		void singleProductDelta() {
			// Arrange
			Long productId = 1L;
			Map<Long, long[]> deltas = Map.of(productId, new long[]{5, 2, 1});
			Set<String> eventIds = new LinkedHashSet<>(Set.of("evt-1", "evt-2"));

			given(rankingRedisPort.incrementCounterAndGetCounts(anyString(), eq(productId), eq(5L), eq(2L), eq(1L)))
				.willReturn(new CounterResult(0, 5, 0, 2, 0, 1));

			// Act
			rankingScoreService.applyDeltas(deltas, eventIds);

			// Assert
			verify(rankingRedisPort).incrementScore(anyString(), eq(productId), doubleThat(d -> d > 0));
			verify(rankingRedisPort).ensureTtl(anyString(), eq(Duration.ofDays(2)));
			verify(eventIdempotencyService).markHandledBatch(eventIds, "ranking-collector");
		}

		@Test
		@DisplayName("[applyDeltas()] scoreDelta=0 -> ZINCRBY 미호출. 점수 변동 없으면 ZSET 미갱신")
		void zeroScoreDelta() {
			// Arrange — old=new (delta 0인 상황)
			Long productId = 1L;
			Map<Long, long[]> deltas = Map.of(productId, new long[]{0, 0, 0});
			Set<String> eventIds = new LinkedHashSet<>(Set.of("evt-1"));

			given(rankingRedisPort.incrementCounterAndGetCounts(anyString(), eq(productId), eq(0L), eq(0L), eq(0L)))
				.willReturn(new CounterResult(5, 5, 2, 2, 1, 1));

			// Act
			rankingScoreService.applyDeltas(deltas, eventIds);

			// Assert
			verify(rankingRedisPort, never()).incrementScore(anyString(), anyLong(), anyDouble());
		}

		@Test
		@DisplayName("[applyDeltas()] 다수 상품 delta -> 각 상품별 Redis 연산 수행")
		void multipleProducts() {
			// Arrange
			Map<Long, long[]> deltas = Map.of(
				1L, new long[]{10, 0, 0},
				2L, new long[]{0, 5, 0}
			);
			Set<String> eventIds = new LinkedHashSet<>(Set.of("evt-1", "evt-2", "evt-3"));

			given(rankingRedisPort.incrementCounterAndGetCounts(anyString(), eq(1L), eq(10L), eq(0L), eq(0L)))
				.willReturn(new CounterResult(0, 10, 0, 0, 0, 0));
			given(rankingRedisPort.incrementCounterAndGetCounts(anyString(), eq(2L), eq(0L), eq(5L), eq(0L)))
				.willReturn(new CounterResult(0, 0, 0, 5, 0, 0));

			// Act
			rankingScoreService.applyDeltas(deltas, eventIds);

			// Assert
			verify(rankingRedisPort, times(2)).incrementCounterAndGetCounts(anyString(), anyLong(), anyLong(), anyLong(), anyLong());
			verify(rankingRedisPort, times(2)).incrementScore(anyString(), anyLong(), anyDouble());
		}
	}


	@Nested
	@DisplayName("findAlreadyHandledIds() 테스트")
	class FindAlreadyHandledIdsTest {

		@Test
		@DisplayName("[findAlreadyHandledIds()] eventIdempotencyService에 위임")
		void delegatesToIdempotencyService() {
			// Arrange
			Set<String> eventIds = Set.of("evt-1", "evt-2");
			given(eventIdempotencyService.findAlreadyHandledIds(eventIds, "ranking-collector"))
				.willReturn(Set.of("evt-1"));

			// Act
			Set<String> result = rankingScoreService.findAlreadyHandledIds(eventIds);

			// Assert
			assertThat(result).containsExactly("evt-1");
		}
	}

}
