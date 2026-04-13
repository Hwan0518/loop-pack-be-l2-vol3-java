package com.loopers.ranking.infrastructure;


import com.loopers.ranking.application.dto.RankingDailyKey;
import com.loopers.ranking.infrastructure.entity.RankingDailyScoreEntity;
import com.loopers.ranking.infrastructure.entity.RankingDailyScoreId;
import com.loopers.ranking.infrastructure.jpa.RankingDailyScoreJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;


@SpringBootTest(properties = {
	"spring.kafka.listener.auto-startup=false"
})
@ActiveProfiles("test")
@Import(MySqlTestContainersConfig.class)
@DisplayName("RankingDailyScoreCommandAdapter 통합 테스트")
class RankingDailyScoreCommandAdapterTest {

	@Autowired
	private RankingDailyScoreCommandAdapter adapter;

	@Autowired
	private RankingDailyScoreJpaRepository jpaRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Test
	@DisplayName("[upsertOrganicScores()] 신규 (date, scorer, product) 키 -> organic_score insert, carry_score=0")
	void upsertNew() {
		// Arrange
		LocalDate date = LocalDate.of(2026, 4, 8);
		Map<RankingDailyKey, Double> scores = Map.of(
			new RankingDailyKey(date, 100L), 0.45
		);

		// Act
		adapter.upsertOrganicScores(scores, "SATURATION");

		// Assert
		RankingDailyScoreEntity row = jpaRepository.findById(new RankingDailyScoreId(date, "SATURATION", 100L)).orElseThrow();
		assertThat(row.getOrganicScore()).isCloseTo(0.45, within(1e-10));
		assertThat(row.getCarryScore()).isCloseTo(0.0, within(1e-10));
	}


	@Test
	@DisplayName("[upsertOrganicScores()] 같은 키 두 번 호출 -> organic_score 만 덮어쓰기, carry_score 유지")
	void upsertOverwritesOrganicOnly() {
		// Arrange
		LocalDate date = LocalDate.of(2026, 4, 8);
		RankingDailyKey key = new RankingDailyKey(date, 100L);

		// 1차: organic=0.3
		adapter.upsertOrganicScores(Map.of(key, 0.3), "SATURATION");

		// carry_score 를 수동으로 세팅 (carry-over 스케줄러 시뮬레이션)
		setCarryScoreDirectly(date, 100L, 0.05);

		// Act — 2차: organic=0.5 (carry_score 유지되어야 함)
		adapter.upsertOrganicScores(Map.of(key, 0.5), "SATURATION");

		// Assert
		RankingDailyScoreEntity row = jpaRepository.findById(new RankingDailyScoreId(date, "SATURATION", 100L)).orElseThrow();
		assertThat(row.getOrganicScore()).isCloseTo(0.5, within(1e-10));
		assertThat(row.getCarryScore()).isCloseTo(0.05, within(1e-10));
	}


	@Test
	@DisplayName("[upsertOrganicScores()] 다수 (date, product) 키 한 번에 -> 모두 적용")
	void multipleEntries() {
		// Arrange
		LocalDate date = LocalDate.of(2026, 4, 8);
		Map<RankingDailyKey, Double> scores = Map.of(
			new RankingDailyKey(date, 100L), 0.3,
			new RankingDailyKey(date, 200L), 0.7
		);

		// Act
		adapter.upsertOrganicScores(scores, "SATURATION");

		// Assert
		assertThat(jpaRepository.findById(new RankingDailyScoreId(date, "SATURATION", 100L)).orElseThrow().getOrganicScore())
			.isCloseTo(0.3, within(1e-10));
		assertThat(jpaRepository.findById(new RankingDailyScoreId(date, "SATURATION", 200L)).orElseThrow().getOrganicScore())
			.isCloseTo(0.7, within(1e-10));
	}


	@Test
	@DisplayName("[upsertOrganicScores()] 빈 Map -> no-op")
	void emptyMap() {
		// Act
		adapter.upsertOrganicScores(Map.of(), "SATURATION");

		// Assert
		assertThat(jpaRepository.count()).isZero();
	}


	// carry_score 직접 업데이트 헬퍼 (carry-over 스케줄러 시뮬레이션)
	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

	private void setCarryScoreDirectly(LocalDate date, Long productId, double carryScore) {
		jdbcTemplate.update(
			"UPDATE ranking_daily_score SET carry_score = ? WHERE stat_date = ? AND scorer_type = ? AND product_id = ?",
			carryScore, java.sql.Date.valueOf(date), "SATURATION", productId
		);
	}

}
