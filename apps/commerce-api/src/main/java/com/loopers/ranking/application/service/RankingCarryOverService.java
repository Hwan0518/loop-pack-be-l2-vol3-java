package com.loopers.ranking.application.service;


import com.loopers.ranking.application.port.out.RankingDailyScoreCarryWritePort;
import com.loopers.ranking.application.port.out.RankingDailyScoreReadPort;
import com.loopers.ranking.application.port.out.RankingDailyScoreReadPort.ScoreSnapshot;
import com.loopers.ranking.application.port.out.RankingRedisPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@Slf4j
public class RankingCarryOverService {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
	private static final double CARRY_OVER_WEIGHT = 0.1;
	private static final Duration TTL = Duration.ofDays(2);

	// port
	private final RankingDailyScoreReadPort scoreReadPort;
	private final RankingDailyScoreCarryWritePort scoreCarryWritePort;
	private final RankingRedisPort rankingRedisPort;
	// 현재 활성 scorer 타입 (설정 주입, 기본값 SATURATION)
	private final String scorerType;


	public RankingCarryOverService(
		RankingDailyScoreReadPort scoreReadPort,
		RankingDailyScoreCarryWritePort scoreCarryWritePort,
		RankingRedisPort rankingRedisPort,
		@Value("${ranking.scorer.type:SATURATION}") String scorerType
	) {
		this.scoreReadPort = scoreReadPort;
		this.scoreCarryWritePort = scoreCarryWritePort;
		this.rankingRedisPort = rankingRedisPort;
		this.scorerType = scorerType;
	}


	/**
	 * 랭킹 콜드 스타트 서비스 — daily_score 기반 carry-over
	 * - 전날 daily_score 의 (organic + carry) * 0.1 을 내일 daily_score 의 carry_score 에 기록
	 * - Redis ZSET 에도 carry 점수 ZADD (멱등 — 재실행 시 덮어쓰기)
	 * - 0 활동 상품도 daily_score row 에 있으면 carry chain 보존
	 *
	 * 1. carry-over 실행
	 */

	// 1. carry-over 실행
	public void carryOver() {
		LocalDate today = LocalDate.now();
		LocalDate tomorrow = today.plusDays(1);

		String todayStr = today.format(DATE_FORMAT);
		String tomorrowStr = tomorrow.format(DATE_FORMAT);

		log.info("[RankingCarryOver] carry-over 시작: {} → {} (weight={}, scorer={})", todayStr, tomorrowStr, CARRY_OVER_WEIGHT, scorerType);

		// 1. 오늘 daily_score 조회
		List<ScoreSnapshot> todayScores = scoreReadPort.findByDateAndScorerType(today, scorerType);

		if (todayScores.isEmpty()) {
			log.info("[RankingCarryOver] 오늘 daily_score row 없음 → carry-over 생략");
			return;
		}

		// 2. carry_score 계산
		Map<Long, Double> carryScores = new HashMap<>();
		Map<Long, Double> redisScores = new HashMap<>();

		for (ScoreSnapshot snapshot : todayScores) {
			double carryValue = snapshot.totalScore() * CARRY_OVER_WEIGHT;
			if (carryValue > 0) {
				carryScores.put(snapshot.productId(), carryValue);
				redisScores.put(snapshot.productId(), carryValue);
			}
		}

		if (carryScores.isEmpty()) {
			log.info("[RankingCarryOver] carry 대상 상품 없음 (모든 점수 0)");
			return;
		}

		// 3. DB: carry_score upsert (organic_score 보존)
		scoreCarryWritePort.upsertCarryScores(tomorrow, scorerType, carryScores);

		// 4. Redis: ZADD + TTL (best-effort — 실패 시 로그 경고, reconcile job(S8)에서 복구 예정)
		// TODO: S8 reconcile job 구현 후 dirty mark 추가 (CARRY_OVER_FAIL reason)
		try {
			rankingRedisPort.batchZadd(tomorrowStr, redisScores);
			rankingRedisPort.setTtl(tomorrowStr, TTL);
		} catch (Exception e) {
			log.error("[RankingCarryOver] Redis carry-over 실패 → 다음 날 ranking 콜드 스타트 누락 위험. reconcile 필요. date={}", tomorrowStr, e);
		}

		log.info("[RankingCarryOver] carry-over 완료: {} → {} ({} 상품)", todayStr, tomorrowStr, carryScores.size());
	}

}
