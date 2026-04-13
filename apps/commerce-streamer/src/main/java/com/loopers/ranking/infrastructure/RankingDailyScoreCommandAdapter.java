package com.loopers.ranking.infrastructure;


import com.loopers.ranking.application.dto.RankingDailyKey;
import com.loopers.ranking.application.port.out.RankingDailyScoreCommandPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * 랭킹 일간 점수 스냅샷 쓰기 어댑터
 * - JdbcTemplate batchUpdate 로 INSERT ... ON DUPLICATE KEY UPDATE
 * - consumer 경로: organic_score 만 갱신 (carry_score 불변)
 *
 * 1. organic_score 배치 upsert
 */

@Repository
@RequiredArgsConstructor
public class RankingDailyScoreCommandAdapter implements RankingDailyScoreCommandPort {

	// organic_score upsert — carry_score 는 INSERT 시 0, UPDATE 시 유지
	private static final String UPSERT_ORGANIC_SQL = """
		INSERT INTO ranking_daily_score
		    (stat_date, scorer_type, product_id, organic_score, carry_score, updated_at)
		VALUES (?, ?, ?, ?, 0, ?)
		ON DUPLICATE KEY UPDATE
		    organic_score = VALUES(organic_score),
		    updated_at    = VALUES(updated_at)
		""";

	// jdbc
	private final JdbcTemplate jdbcTemplate;


	// 1. organic_score 배치 upsert
	@Override
	public void upsertOrganicScores(Map<RankingDailyKey, Double> organicScores, String scorerType) {
		if (organicScores == null || organicScores.isEmpty()) {
			return;
		}

		List<Map.Entry<RankingDailyKey, Double>> entries = new ArrayList<>(organicScores.entrySet());
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());

		jdbcTemplate.batchUpdate(UPSERT_ORGANIC_SQL, new BatchPreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps, int i) throws SQLException {
				Map.Entry<RankingDailyKey, Double> entry = entries.get(i);
				RankingDailyKey key = entry.getKey();

				ps.setDate(1, Date.valueOf(key.statDate()));
				ps.setString(2, scorerType);
				ps.setLong(3, key.productId());
				ps.setDouble(4, entry.getValue());
				ps.setTimestamp(5, now);
			}

			@Override
			public int getBatchSize() {
				return entries.size();
			}
		});
	}

}
