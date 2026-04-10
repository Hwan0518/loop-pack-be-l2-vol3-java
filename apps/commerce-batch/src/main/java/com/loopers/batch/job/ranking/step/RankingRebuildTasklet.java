package com.loopers.batch.job.ranking.step;


import com.loopers.batch.job.ranking.scorer.RankingScorer;
import com.loopers.config.redis.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;


/**
 * 랭킹 재계산 Tasklet
 * - Job Parameters: from, to, scorerType, carryOverWeight
 * - 날짜 순차 처리 (from → to) — carry-over chain 보존
 * - daily_counter 읽기 → scorer 재계산 → daily_score 덮어쓰기 → Redis ZSET 재생성
 *
 * 1. 날짜 루프 (from..to 순차)
 * 2. 단일 날짜 rebuild
 */

@StepScope
@Component
@Slf4j
public class RankingRebuildTasklet implements Tasklet {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
	private static final String ZSET_PREFIX = "ranking:all:";
	private static final Duration TTL = Duration.ofDays(2);

	// jdbc
	private final JdbcTemplate jdbcTemplate;
	// redis (master)
	private final RedisTemplate<String, String> redisTemplate;
	// scorer map
	private final Map<String, RankingScorer> rankingScorerMap;


	public RankingRebuildTasklet(
		JdbcTemplate jdbcTemplate,
		@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
		Map<String, RankingScorer> rankingScorerMap
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.redisTemplate = redisTemplate;
		this.rankingScorerMap = rankingScorerMap;
	}

	@Value("#{jobParameters['from']}")
	private String fromStr;

	@Value("#{jobParameters['to']}")
	private String toStr;

	@Value("#{jobParameters['scorerType'] ?: 'SATURATION'}")
	private String scorerType;

	@Value("#{jobParameters['carryOverWeight'] ?: '0.1'}")
	private String carryOverWeightStr;


	// 1. 날짜 루프
	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		LocalDate from = LocalDate.parse(fromStr, DATE_FORMAT);
		LocalDate to = LocalDate.parse(toStr, DATE_FORMAT);
		double carryOverWeight = Double.parseDouble(carryOverWeightStr);

		RankingScorer scorer = rankingScorerMap.get(scorerType);
		if (scorer == null) {
			throw new IllegalArgumentException("지원하지 않는 scorer: " + scorerType + ", 가능한 값: " + rankingScorerMap.keySet());
		}

		log.info("[RankingRebuild] 시작: {} → {}, scorer={}, carryWeight={}", fromStr, toStr, scorerType, carryOverWeight);

		// 전날 최종 점수 맵 (carry chain 전파용)
		Map<Long, Double> prevDayScores = loadPrevDayScores(from.minusDays(1));

		int totalProducts = 0;
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			int count = rebuildDay(date, scorer, prevDayScores, carryOverWeight);
			totalProducts += count;
		}

		log.info("[RankingRebuild] 완료: {} → {}, 총 {}건 처리", fromStr, toStr, totalProducts);
		contribution.incrementWriteCount(totalProducts);

		return RepeatStatus.FINISHED;
	}


	// 2. 단일 날짜 rebuild
	public int rebuildDay(LocalDate date, RankingScorer scorer, Map<Long, Double> prevDayScores, double carryOverWeight) {
		String dateStr = date.format(DATE_FORMAT);

		// daily_counter 조회
		List<CounterRow> counters = jdbcTemplate.query(
			"SELECT product_id, view_count, like_count, order_qty FROM ranking_daily_counter WHERE stat_date = ?",
			(rs, rowNum) -> new CounterRow(
				rs.getLong("product_id"),
				rs.getLong("view_count"),
				rs.getLong("like_count"),
				rs.getLong("order_qty")
			),
			Date.valueOf(date)
		);

		// 모든 관련 productId 수집 (counter + prevDay carry 대상)
		Set<Long> allProductIds = new LinkedHashSet<>();
		for (CounterRow c : counters) {
			allProductIds.add(c.productId);
		}
		allProductIds.addAll(prevDayScores.keySet());

		if (allProductIds.isEmpty()) {
			log.debug("[RankingRebuild] {} — counter/carry 모두 없음, skip", dateStr);
			prevDayScores.clear();
			return 0;
		}

		// counter 를 Map 으로 변환
		Map<Long, CounterRow> counterMap = new HashMap<>();
		for (CounterRow c : counters) {
			counterMap.put(c.productId, c);
		}

		// 점수 계산 + daily_score upsert + Redis ZADD 준비
		Map<Long, Double> todayScores = new HashMap<>();
		List<Object[]> scoreUpsertParams = new ArrayList<>();

		for (Long productId : allProductIds) {
			// organic
			CounterRow c = counterMap.get(productId);
			double organic = 0.0;
			if (c != null) {
				organic = scorer.calculateScore(clamp(c.view), clamp(c.like), clamp(c.order));
			}

			// carry
			double carry = 0.0;
			Double prevTotal = prevDayScores.get(productId);
			if (prevTotal != null && prevTotal > 0) {
				carry = prevTotal * carryOverWeight;
			}

			double total = organic + carry;
			todayScores.put(productId, total);

			scoreUpsertParams.add(new Object[]{
				Date.valueOf(date), scorerType, productId, organic, carry
			});
		}

		// daily_score upsert (전체 덮어쓰기)
		jdbcTemplate.batchUpdate(
			"INSERT INTO ranking_daily_score (stat_date, scorer_type, product_id, organic_score, carry_score, updated_at) " +
			"VALUES (?, ?, ?, ?, ?, NOW()) " +
			"ON DUPLICATE KEY UPDATE organic_score = VALUES(organic_score), carry_score = VALUES(carry_score), updated_at = NOW()",
			scoreUpsertParams
		);

		// Redis ZSET 재생성 (기존 키 삭제 → 새로 ZADD)
		String zsetKey = ZSET_PREFIX + dateStr;
		redisTemplate.delete(zsetKey);
		for (Map.Entry<Long, Double> entry : todayScores.entrySet()) {
			if (entry.getValue() > 0) {
				redisTemplate.opsForZSet().add(zsetKey, entry.getKey().toString(), entry.getValue());
			}
		}
		redisTemplate.expire(zsetKey, TTL);

		log.info("[RankingRebuild] {} — {} 상품 rebuild 완료", dateStr, allProductIds.size());

		// 다음 날 carry 전파용으로 교체
		prevDayScores.clear();
		prevDayScores.putAll(todayScores);

		return allProductIds.size();
	}


	/**
	 * private method
	 * - 전날 최종 점수 맵 로딩 (from-1 일자)
	 * - 음수 카운터 clamp
	 */

	// 전날 최종 점수 맵 (from-1 일자 daily_score 에서 로딩)
	private Map<Long, Double> loadPrevDayScores(LocalDate prevDay) {
		Map<Long, Double> map = new HashMap<>();
		jdbcTemplate.query(
			"SELECT product_id, organic_score, carry_score FROM ranking_daily_score " +
			"WHERE stat_date = ? AND scorer_type = ?",
			(rs) -> {
				Long productId = rs.getLong("product_id");
				double total = rs.getDouble("organic_score") + rs.getDouble("carry_score");
				map.put(productId, total);
			},
			Date.valueOf(prevDay), scorerType
		);

		if (map.isEmpty()) {
			log.warn("[RankingRebuild] from-1({}) 일자 daily_score 없음 → carry=0 으로 시작", prevDay);
		}
		return map;
	}


	// 음수 clamp
	private static long clamp(long value) {
		return Math.max(0, value);
	}


	// counter row record
	private record CounterRow(Long productId, long view, long like, long order) {
	}

}
