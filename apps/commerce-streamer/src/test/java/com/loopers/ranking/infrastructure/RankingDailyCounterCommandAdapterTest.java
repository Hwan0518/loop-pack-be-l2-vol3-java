package com.loopers.ranking.infrastructure;


import com.loopers.ranking.application.dto.RankingDailyKey;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.ranking.infrastructure.entity.RankingDailyCounterEntity;
import com.loopers.ranking.infrastructure.entity.RankingDailyCounterId;
import com.loopers.ranking.infrastructure.jpa.RankingDailyCounterJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(properties = {
	"spring.kafka.listener.auto-startup=false"
})
@ActiveProfiles("test")
@Import(MySqlTestContainersConfig.class)
@DisplayName("RankingDailyCounterCommandAdapter 통합 테스트")
class RankingDailyCounterCommandAdapterTest {

	@Autowired
	private RankingDailyCounterCommandAdapter adapter;

	@Autowired
	private RankingDailyCounterJpaRepository jpaRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Test
	@DisplayName("[upsertDeltas()] 신규 (date,product) 키 -> view/like/order 그대로 insert")
	void upsertNew() {
		// Arrange
		LocalDate date = LocalDate.of(2026, 4, 8);
		Map<RankingDailyKey, long[]> deltas = Map.of(
			new RankingDailyKey(date, 100L), new long[]{5, 2, 1}
		);

		// Act
		adapter.upsertDeltas(deltas);

		// Assert
		RankingDailyCounterEntity row = jpaRepository.findById(new RankingDailyCounterId(date, 100L)).orElseThrow();
		assertThat(row.getViewCount()).isEqualTo(5L);
		assertThat(row.getLikeCount()).isEqualTo(2L);
		assertThat(row.getOrderQty()).isEqualTo(1L);
	}


	@Test
	@DisplayName("[upsertDeltas()] 같은 (date,product) 두 번 호출 -> 누적 합산")
	void upsertAccumulates() {
		// Arrange
		LocalDate date = LocalDate.of(2026, 4, 8);
		RankingDailyKey key = new RankingDailyKey(date, 100L);

		// Act
		adapter.upsertDeltas(Map.of(key, new long[]{5, 2, 1}));
		adapter.upsertDeltas(Map.of(key, new long[]{3, 1, 2}));

		// Assert
		RankingDailyCounterEntity row = jpaRepository.findById(new RankingDailyCounterId(date, 100L)).orElseThrow();
		assertThat(row.getViewCount()).isEqualTo(8L);
		assertThat(row.getLikeCount()).isEqualTo(3L);
		assertThat(row.getOrderQty()).isEqualTo(3L);
	}


	@Test
	@DisplayName("[upsertDeltas()] 음수 like delta 누적 -> 결과가 음수여도 그대로 저장 (clamp 없음)")
	void negativeAccumulationAllowed() {
		// Arrange
		LocalDate date = LocalDate.of(2026, 4, 8);
		RankingDailyKey key = new RankingDailyKey(date, 100L);

		// Act — 좋아요 1개 → 취소 2개
		adapter.upsertDeltas(Map.of(key, new long[]{0, 1, 0}));
		adapter.upsertDeltas(Map.of(key, new long[]{0, -2, 0}));

		// Assert
		RankingDailyCounterEntity row = jpaRepository.findById(new RankingDailyCounterId(date, 100L)).orElseThrow();
		assertThat(row.getLikeCount()).isEqualTo(-1L);
	}


	@Test
	@DisplayName("[upsertDeltas()] 다수 (date,product) 한 번에 -> 모두 적용")
	void multipleEntries() {
		// Arrange — 두 날짜, 두 상품 섞어서
		LocalDate dateA = LocalDate.of(2026, 4, 8);
		LocalDate dateB = LocalDate.of(2026, 4, 9);
		Map<RankingDailyKey, long[]> deltas = new LinkedHashMap<>();
		deltas.put(new RankingDailyKey(dateA, 100L), new long[]{1, 0, 0});
		deltas.put(new RankingDailyKey(dateA, 200L), new long[]{0, 1, 0});
		deltas.put(new RankingDailyKey(dateB, 100L), new long[]{0, 0, 1});

		// Act
		adapter.upsertDeltas(deltas);

		// Assert
		assertThat(jpaRepository.findById(new RankingDailyCounterId(dateA, 100L)).orElseThrow().getViewCount()).isEqualTo(1L);
		assertThat(jpaRepository.findById(new RankingDailyCounterId(dateA, 200L)).orElseThrow().getLikeCount()).isEqualTo(1L);
		assertThat(jpaRepository.findById(new RankingDailyCounterId(dateB, 100L)).orElseThrow().getOrderQty()).isEqualTo(1L);
	}


	@Test
	@DisplayName("[upsertDeltas()] 빈 Map -> no-op")
	void emptyMap() {
		// Act
		adapter.upsertDeltas(Map.of());

		// Assert — 예외 없이 통과
		assertThat(jpaRepository.count()).isZero();
	}

}
