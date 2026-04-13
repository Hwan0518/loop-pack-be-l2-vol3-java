package com.loopers.ranking.infrastructure;


import com.loopers.ranking.application.dto.RankingDailyKey;
import com.loopers.ranking.application.port.out.RankingDailyCounterCommandPort;
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
import java.util.*;


/**
 * 랭킹 일간 카운터 쓰기 어댑터
 * - JdbcTemplate batchUpdate 로 INSERT ... ON DUPLICATE KEY UPDATE 일괄 실행
 * - count 컬럼은 raw signed 누적 (clamp 없음)
 *
 * 1. 배치 upsert (delta 누적)
 */

@Repository
@RequiredArgsConstructor
public class RankingDailyCounterCommandAdapter implements RankingDailyCounterCommandPort {

	private static final String UPSERT_SQL = """
		INSERT INTO ranking_daily_counter
		    (stat_date, product_id, view_count, like_count, order_qty, updated_at)
		VALUES (?, ?, ?, ?, ?, ?)
		ON DUPLICATE KEY UPDATE
		    view_count = view_count + VALUES(view_count),
		    like_count = like_count + VALUES(like_count),
		    order_qty  = order_qty  + VALUES(order_qty),
		    updated_at = VALUES(updated_at)
		""";

	// jdbc
	private final JdbcTemplate jdbcTemplate;


	/**
	 * 1. 배치 upsert (delta 누적)
	 * 2. 현재 누적 카운터 조회
	 */

	// 1. 배치 upsert
	@Override
	public void upsertDeltas(Map<RankingDailyKey, long[]> deltas) {
		if (deltas == null || deltas.isEmpty()) {
			return;
		}

		// 안정적 순서 보장 — entry list 로 평탄화
		List<Map.Entry<RankingDailyKey, long[]>> entries = new ArrayList<>(deltas.entrySet());
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());

		jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps, int i) throws SQLException {
				Map.Entry<RankingDailyKey, long[]> entry = entries.get(i);
				RankingDailyKey key = entry.getKey();
				long[] delta = entry.getValue(); // [view, like, order]

				ps.setDate(1, Date.valueOf(key.statDate()));
				ps.setLong(2, key.productId());
				ps.setLong(3, delta[0]);
				ps.setLong(4, delta[1]);
				ps.setLong(5, delta[2]);
				ps.setTimestamp(6, now);
			}

			@Override
			public int getBatchSize() {
				return entries.size();
			}
		});
	}


	// 2. 현재 누적 카운터 조회
	@Override
	public Map<RankingDailyKey, long[]> getCounters(Set<RankingDailyKey> keys) {
		if (keys == null || keys.isEmpty()) {
			return Map.of();
		}

		// (stat_date, product_id) 쌍을 IN clause 로 구성
		List<Object> params = new ArrayList<>();
		StringJoiner joiner = new StringJoiner(",");

		for (RankingDailyKey key : keys) {
			joiner.add("(?,?)");
			params.add(Date.valueOf(key.statDate()));
			params.add(key.productId());
		}

		String sql = "SELECT stat_date, product_id, view_count, like_count, order_qty " +
			"FROM ranking_daily_counter WHERE (stat_date, product_id) IN (" + joiner + ")";

		Map<RankingDailyKey, long[]> result = new HashMap<>();
		jdbcTemplate.query(sql, params.toArray(), rs -> {
			LocalDate statDate = rs.getDate("stat_date").toLocalDate();
			Long productId = rs.getLong("product_id");
			long[] counts = new long[]{
				rs.getLong("view_count"),
				rs.getLong("like_count"),
				rs.getLong("order_qty")
			};
			result.put(new RankingDailyKey(statDate, productId), counts);
		});

		return result;
	}

}
