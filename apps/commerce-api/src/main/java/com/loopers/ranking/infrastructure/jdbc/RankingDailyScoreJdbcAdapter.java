package com.loopers.ranking.infrastructure.jdbc;


import com.loopers.ranking.application.port.out.RankingDailyScoreCarryWritePort;
import com.loopers.ranking.application.port.out.RankingDailyScoreReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * 랭킹 일간 점수 스냅샷 JDBC 어댑터 (commerce-api)
 * - JPA Entity 없이 JdbcTemplate 으로 ranking_daily_score 테이블 직접 접근
 * - 읽기 (carry-over 전날 조회) + 쓰기 (carry_score upsert)
 *
 * 1. 특정 날짜 전체 점수 스냅샷 조회
 * 2. carry_score 배치 upsert
 */

@Repository
@RequiredArgsConstructor
public class RankingDailyScoreJdbcAdapter implements RankingDailyScoreReadPort, RankingDailyScoreCarryWritePort {

	// jdbc
	private final JdbcTemplate jdbcTemplate;


	// 1. 특정 날짜 + scorerType 전체 점수 스냅샷 조회
	@Override
	public List<ScoreSnapshot> findByDateAndScorerType(LocalDate statDate, String scorerType) {
		return jdbcTemplate.query(
			"SELECT product_id, organic_score, carry_score " +
			"FROM ranking_daily_score " +
			"WHERE stat_date = ? AND scorer_type = ?",
			(rs, rowNum) -> new ScoreSnapshot(
				rs.getLong("product_id"),
				rs.getDouble("organic_score"),
				rs.getDouble("carry_score")
			),
			Date.valueOf(statDate), scorerType
		);
	}


	// 2. carry_score 배치 upsert — organic_score 보존
	private static final String UPSERT_CARRY_SQL = """
		INSERT INTO ranking_daily_score
		    (stat_date, scorer_type, product_id, organic_score, carry_score, updated_at)
		VALUES (?, ?, ?, 0, ?, ?)
		ON DUPLICATE KEY UPDATE
		    carry_score = VALUES(carry_score),
		    updated_at  = VALUES(updated_at)
		""";

	@Override
	public void upsertCarryScores(LocalDate statDate, String scorerType, Map<Long, Double> carryScores) {
		if (carryScores == null || carryScores.isEmpty()) {
			return;
		}

		List<Map.Entry<Long, Double>> entries = new ArrayList<>(carryScores.entrySet());
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());

		jdbcTemplate.batchUpdate(UPSERT_CARRY_SQL, new BatchPreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps, int i) throws SQLException {
				Map.Entry<Long, Double> entry = entries.get(i);
				ps.setDate(1, Date.valueOf(statDate));
				ps.setString(2, scorerType);
				ps.setLong(3, entry.getKey());
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
