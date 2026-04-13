package com.loopers.ranking.application.service;


import com.loopers.ranking.application.dto.RankingDailyKey;
import com.loopers.ranking.application.port.out.*;
import com.loopers.support.idempotency.EventIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;


@Service
@RequiredArgsConstructor
@Slf4j
public class RankingScoreService {

	private static final String CONSUMER_GROUP = "ranking-collector";
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
	private static final Duration TTL = Duration.ofDays(2);

	// port — DB
	private final RankingDailyCounterCommandPort counterPort;
	private final RankingDailyScoreCommandPort scorePort;
	private final RankingProjectionDirtyPort dirtyPort;
	// port — Redis
	private final RankingRedisPort rankingRedisPort;
	// scorer (교체 가능 — SaturationScorer, LinearScorer 등)
	private final RankingScorer rankingScorer;
	// idempotency
	private final EventIdempotencyService eventIdempotencyService;


	/**
	 * 랭킹 점수 집계 서비스
	 * - DB 단일 TX: counter upsert + organic_score upsert + event_handled
	 * - Redis 는 best-effort (TX 외부, consumer 에서 호출)
	 *
	 * 1. DB 단일 TX (counter + score + event_handled)
	 * 2. Redis 반영 (best-effort, consumer 에서 try-catch 호출)
	 * 3. projection dirty mark (Redis 실패 시)
	 * 4. 이미 처리된 eventId 집합 조회
	 */

	// 1. DB 단일 TX — counter upsert + organic_score upsert + event_handled
	@Transactional
	public void persistDeltas(Map<RankingDailyKey, long[]> deltas, Set<String> newEventIds) {
		// counter upsert (delta 누적)
		counterPort.upsertDeltas(deltas);

		// read-back — DB 기준 절대 카운터 값
		Map<RankingDailyKey, long[]> counters = counterPort.getCounters(deltas.keySet());

		// organic_score 계산 (clamp → scorer)
		Map<RankingDailyKey, Double> organicScores = new HashMap<>();
		for (Map.Entry<RankingDailyKey, long[]> entry : counters.entrySet()) {
			long[] c = entry.getValue();
			double organic = rankingScorer.calculateScore(clamp(c[0]), clamp(c[1]), clamp(c[2]));
			organicScores.put(entry.getKey(), organic);
		}

		// organic_score upsert (carry_score 미변경)
		scorePort.upsertOrganicScores(organicScores, rankingScorer.scorerType());

		// event_handled bulk insert
		eventIdempotencyService.markHandledBatch(newEventIds, CONSUMER_GROUP);
	}


	// 2. Redis 반영 (best-effort — consumer 에서 try-catch 로 호출)
	public void reflectToRedis(Map<RankingDailyKey, long[]> deltas) {
		Set<LocalDate> affectedDates = new HashSet<>();

		for (Map.Entry<RankingDailyKey, long[]> entry : deltas.entrySet()) {
			RankingDailyKey key = entry.getKey();
			Long productId = key.productId();
			long[] delta = entry.getValue();
			String dateStr = key.statDate().format(DATE_FORMAT);

			// Redis 카운터 갱신 + old/new 카운트 획득
			CounterResult counts = rankingRedisPort.incrementCounterAndGetCounts(
				dateStr, productId, delta[0], delta[1], delta[2]);

			// scorer delta 계산
			double scoreDelta = computeScoreDelta(counts);

			// ZSET 점수 증분
			if (scoreDelta != 0.0) {
				rankingRedisPort.incrementScore(dateStr, productId, scoreDelta);
			}

			affectedDates.add(key.statDate());
		}

		// TTL 보장 (날짜별 1회)
		for (LocalDate date : affectedDates) {
			rankingRedisPort.ensureTtl(date.format(DATE_FORMAT), TTL);
		}
	}


	// 3. projection dirty mark (Redis 실패 시, 별도 TX — consumer 에서 호출)
	public void markProjectionDirty(Set<LocalDate> dates) {
		dirtyPort.markDirty(dates, "REDIS_WRITE_FAIL");
	}


	// 4. 이미 처리된 eventId 집합 조회
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
