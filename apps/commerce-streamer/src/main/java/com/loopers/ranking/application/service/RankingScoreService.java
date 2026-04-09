package com.loopers.ranking.application.service;


import com.loopers.ranking.application.port.out.CounterResult;
import com.loopers.ranking.application.port.out.RankingRedisPort;
import com.loopers.ranking.application.port.out.RankingScorer;
import com.loopers.support.idempotency.EventIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;


@Service
@RequiredArgsConstructor
@Slf4j
public class RankingScoreService {

	private static final String CONSUMER_GROUP = "ranking-collector";
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
	private static final Duration TTL = Duration.ofDays(2);

	// port
	private final RankingRedisPort rankingRedisPort;
	// scorer (교체 가능 — SaturationScorer, LinearScorer 등)
	private final RankingScorer rankingScorer;
	// idempotency
	private final EventIdempotencyService eventIdempotencyService;


	/**
	 * 랭킹 점수 집계 서비스
	 * - RankingScorer 인터페이스를 통해 점수 계산 정책을 교체할 수 있음
	 * - Redis 카운터 갱신 → delta 계산 → ZSET 반영 → event_handled 기록
	 *
	 * 1. 배치 delta 반영 (카운터 갱신 + scorer delta + ZSET + event_handled)
	 * 2. event_handled 일괄 기록
	 * 3. 이미 처리된 eventId 집합 조회
	 */

	// 1. 배치 delta 반영
	public void applyDeltas(Map<Long, long[]> deltas, Set<String> newEventIds) {
		String dateStr = LocalDate.now().format(DATE_FORMAT);

		// Redis 쓰기 (TX 밖 — 멱등 필터가 재처리 방지)
		for (Map.Entry<Long, long[]> entry : deltas.entrySet()) {
			Long productId = entry.getKey();
			long[] delta = entry.getValue(); // [viewDelta, likeDelta, orderDelta]

			// 카운터 갱신 + old/new 카운트 획득
			CounterResult counts = rankingRedisPort.incrementCounterAndGetCounts(
				dateStr, productId, delta[0], delta[1], delta[2]);

			// scorer delta 계산
			double scoreDelta = computeScoreDelta(counts);

			// ZSET 점수 증분
			if (scoreDelta != 0.0) {
				rankingRedisPort.incrementScore(dateStr, productId, scoreDelta);
			}
		}

		// TTL 보장
		rankingRedisPort.ensureTtl(dateStr, TTL);

		// event_handled 기록 (DB TX)
		markEventHandled(newEventIds);
	}


	// 2. event_handled 일괄 기록 (별도 TX)
	@Transactional
	public void markEventHandled(Set<String> eventIds) {
		eventIdempotencyService.markHandledBatch(eventIds, CONSUMER_GROUP);
	}


	// 3. 이미 처리된 eventId 집합 조회
	public Set<String> findAlreadyHandledIds(Set<String> eventIds) {
		return eventIdempotencyService.findAlreadyHandledIds(eventIds, CONSUMER_GROUP);
	}


	/**
	 * private method
	 * - 음수 카운터 보정 (clamp)
	 * - scoreDelta 계산 (scorer 위임)
	 */

	// 음수 카운터 보정 — unlike 중복 등으로 카운터가 음수가 된 경우 0으로 clamp
	private static long clamp(long value) {
		return Math.max(0, value);
	}


	// scoreDelta = scorer.calculateScore(new) - scorer.calculateScore(old)
	private double computeScoreDelta(CounterResult c) {
		double oldScore = rankingScorer.calculateScore(clamp(c.oldView()), clamp(c.oldLike()), clamp(c.oldOrder()));
		double newScore = rankingScorer.calculateScore(clamp(c.newView()), clamp(c.newLike()), clamp(c.newOrder()));
		return newScore - oldScore;
	}

}
