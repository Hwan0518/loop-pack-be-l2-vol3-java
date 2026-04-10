package com.loopers.ranking.infrastructure;


import com.loopers.ranking.application.port.out.RankingProjectionDirtyPort;
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
import java.util.Set;


/**
 * 랭킹 projection dirty 쓰기 어댑터
 * - JdbcTemplate 로 INSERT ... ON DUPLICATE KEY UPDATE (marked_at 갱신)
 *
 * 1. dirty mark
 */

@Repository
@RequiredArgsConstructor
public class RankingProjectionDirtyAdapter implements RankingProjectionDirtyPort {

	private static final String UPSERT_SQL = """
		INSERT INTO ranking_projection_dirty
		    (stat_date, reason, marked_at, resolved_at)
		VALUES (?, ?, ?, NULL)
		ON DUPLICATE KEY UPDATE
		    marked_at   = VALUES(marked_at),
		    resolved_at = NULL
		""";

	// jdbc
	private final JdbcTemplate jdbcTemplate;


	// 1. dirty mark
	@Override
	public void markDirty(Set<LocalDate> dates, String reason) {
		if (dates == null || dates.isEmpty()) {
			return;
		}

		List<LocalDate> dateList = new ArrayList<>(dates);
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());

		jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps, int i) throws SQLException {
				ps.setDate(1, Date.valueOf(dateList.get(i)));
				ps.setString(2, reason);
				ps.setTimestamp(3, now);
			}

			@Override
			public int getBatchSize() {
				return dateList.size();
			}
		});
	}

}
