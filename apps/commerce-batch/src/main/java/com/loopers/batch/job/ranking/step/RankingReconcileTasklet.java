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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 랭킹 Redis projection 복구 Tasklet
 * - ranking_projection_dirty 에서 resolved_at IS NULL 인 row 를 읽어
 * - 각 날짜에 대해 RankingRebuildTasklet.rebuildDay() 와 동일한 로직으로 Redis 재생성
 * - 완료 후 resolved_at 갱신
 *
 * 1. dirty 날짜 조회 → 날짜별 rebuild → resolved_at 갱신
 */

@StepScope
@Component
@Slf4j
public class RankingReconcileTasklet implements Tasklet {

	// jdbc
	private final JdbcTemplate jdbcTemplate;
	// redis (master)
	private final RedisTemplate<String, String> redisTemplate;
	// scorer map
	private final Map<String, RankingScorer> rankingScorerMap;
	// rebuild 위임 (동일 로직 재사용)
	private final RankingRebuildTasklet rebuildTasklet;

	@Value("${ranking.scorer.type:SATURATION}")
	private String scorerType;

	@Value("${ranking.carryover.weight:0.1}")
	private double carryOverWeight;


	public RankingReconcileTasklet(
		JdbcTemplate jdbcTemplate,
		@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
		Map<String, RankingScorer> rankingScorerMap,
		RankingRebuildTasklet rebuildTasklet
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.redisTemplate = redisTemplate;
		this.rankingScorerMap = rankingScorerMap;
		this.rebuildTasklet = rebuildTasklet;
	}


	// 1. dirty 날짜 조회 → 날짜별 rebuild → resolved_at 갱신
	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		// unresolved dirty dates 조회
		List<LocalDate> dirtyDates = jdbcTemplate.query(
			"SELECT DISTINCT stat_date FROM ranking_projection_dirty WHERE resolved_at IS NULL ORDER BY stat_date",
			(rs, rowNum) -> rs.getDate("stat_date").toLocalDate()
		);

		if (dirtyDates.isEmpty()) {
			log.info("[RankingReconcile] dirty date 없음 — 정상");
			return RepeatStatus.FINISHED;
		}

		log.info("[RankingReconcile] 복구 대상: {} 날짜", dirtyDates.size());

		RankingScorer scorer = rankingScorerMap.get(scorerType);
		if (scorer == null) {
			throw new IllegalArgumentException("지원하지 않는 scorer: " + scorerType);
		}

		int totalProducts = 0;
		for (LocalDate date : dirtyDates) {
			// 전날 score 로딩 (carry chain 용)
			Map<Long, Double> prevDayScores = loadPrevDayScores(date.minusDays(1));

			// rebuildDay 위임
			int count = rebuildTasklet.rebuildDay(date, scorer, prevDayScores, carryOverWeight);
			totalProducts += count;

			// resolved_at 갱신
			jdbcTemplate.update(
				"UPDATE ranking_projection_dirty SET resolved_at = NOW() WHERE stat_date = ? AND resolved_at IS NULL",
				Date.valueOf(date)
			);

			log.info("[RankingReconcile] {} 복구 완료 ({} 상품)", date, count);
		}

		contribution.incrementWriteCount(totalProducts);
		log.info("[RankingReconcile] 전체 복구 완료: {} 날짜, {} 상품", dirtyDates.size(), totalProducts);

		return RepeatStatus.FINISHED;
	}


	// 전날 최종 점수 맵
	private Map<Long, Double> loadPrevDayScores(LocalDate prevDay) {
		Map<Long, Double> map = new HashMap<>();
		jdbcTemplate.query(
			"SELECT product_id, organic_score, carry_score FROM ranking_daily_score " +
			"WHERE stat_date = ? AND scorer_type = ?",
			(rs) -> {
				map.put(rs.getLong("product_id"), rs.getDouble("organic_score") + rs.getDouble("carry_score"));
			},
			Date.valueOf(prevDay), scorerType
		);
		return map;
	}

}
