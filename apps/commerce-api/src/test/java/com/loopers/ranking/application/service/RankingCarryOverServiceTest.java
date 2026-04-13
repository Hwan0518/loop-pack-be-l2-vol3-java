package com.loopers.ranking.application.service;


import com.loopers.ranking.application.port.out.RankingDailyScoreCarryWritePort;
import com.loopers.ranking.application.port.out.RankingDailyScoreReadPort;
import com.loopers.ranking.application.port.out.RankingDailyScoreReadPort.ScoreSnapshot;
import com.loopers.ranking.application.port.out.RankingRedisPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("RankingCarryOverService 단위 테스트")
class RankingCarryOverServiceTest {

	@Mock
	private RankingDailyScoreReadPort scoreReadPort;

	@Mock
	private RankingDailyScoreCarryWritePort scoreCarryWritePort;

	@Mock
	private RankingRedisPort rankingRedisPort;

	private RankingCarryOverService rankingCarryOverService;

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
	private static final LocalDate TODAY = LocalDate.now();
	private static final LocalDate TOMORROW = TODAY.plusDays(1);
	private static final String SCORER_TYPE = "SATURATION";


	@BeforeEach
	void setUp() {
		rankingCarryOverService = new RankingCarryOverService(scoreReadPort, scoreCarryWritePort, rankingRedisPort, SCORER_TYPE);
	}


	@Test
	@DisplayName("[carryOver()] 오늘 daily_score 있음 -> (organic+carry)*0.1 로 내일 carry_score upsert + Redis ZADD")
	void carryOverExecutes() {
		// Arrange — 오늘 2개 상품
		given(scoreReadPort.findByDateAndScorerType(TODAY, SCORER_TYPE))
			.willReturn(List.of(
				new ScoreSnapshot(100L, 0.4, 0.05),  // total=0.45, carry=0.045
				new ScoreSnapshot(200L, 0.2, 0.0)    // total=0.2, carry=0.02
			));

		// Act
		rankingCarryOverService.carryOver();

		// Assert — DB carry_score upsert
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<Long, Double>> carryCaptor = ArgumentCaptor.forClass(Map.class);
		verify(scoreCarryWritePort).upsertCarryScores(eq(TOMORROW), eq(SCORER_TYPE), carryCaptor.capture());

		Map<Long, Double> carryScores = carryCaptor.getValue();
		assertThat(carryScores.get(100L)).isCloseTo(0.045, within(1e-10));
		assertThat(carryScores.get(200L)).isCloseTo(0.02, within(1e-10));

		// Assert — Redis ZADD + TTL
		verify(rankingRedisPort).batchZadd(eq(TOMORROW.format(DATE_FORMAT)), anyMap());
		verify(rankingRedisPort).setTtl(eq(TOMORROW.format(DATE_FORMAT)), eq(Duration.ofDays(2)));
	}


	@Test
	@DisplayName("[carryOver()] 오늘 daily_score 없음 -> carry-over 생략, 쓰기 미발생")
	void noScoresSkipsCarryOver() {
		// Arrange
		given(scoreReadPort.findByDateAndScorerType(TODAY, SCORER_TYPE))
			.willReturn(List.of());

		// Act
		rankingCarryOverService.carryOver();

		// Assert
		verify(scoreCarryWritePort, never()).upsertCarryScores(any(), any(), anyMap());
		verify(rankingRedisPort, never()).batchZadd(anyString(), anyMap());
	}


	@Test
	@DisplayName("[carryOver()] 모든 상품 점수 0 -> carry 대상 없음, 쓰기 미발생")
	void allZeroScoresSkipsCarryOver() {
		// Arrange — organic=0, carry=0
		given(scoreReadPort.findByDateAndScorerType(TODAY, SCORER_TYPE))
			.willReturn(List.of(new ScoreSnapshot(100L, 0.0, 0.0)));

		// Act
		rankingCarryOverService.carryOver();

		// Assert — carry = 0*0.1 = 0, 필터링됨
		verify(scoreCarryWritePort, never()).upsertCarryScores(any(), any(), anyMap());
	}


	@Test
	@DisplayName("[carryOver()] Redis 실패 -> DB carry_score는 저장됨, 예외 전파 없음")
	void redisFailureDoesNotBlockDbWrite() {
		// Arrange
		given(scoreReadPort.findByDateAndScorerType(TODAY, SCORER_TYPE))
			.willReturn(List.of(new ScoreSnapshot(100L, 0.5, 0.0)));
		doThrow(new RuntimeException("Redis down")).when(rankingRedisPort).batchZadd(anyString(), anyMap());

		// Act — 예외 전파 없이 정상 종료
		rankingCarryOverService.carryOver();

		// Assert — DB는 정상 저장
		verify(scoreCarryWritePort).upsertCarryScores(eq(TOMORROW), eq(SCORER_TYPE), anyMap());
	}


	@Test
	@DisplayName("[carryOver()] 0 활동 상품이 carry 통해 살아있음 -> chain 보존 (carry-only 상품)")
	void carryOnlyProductPreserved() {
		// Arrange — organic=0 (오늘 활동 없음), carry=0.05 (어제서 carry 받음)
		given(scoreReadPort.findByDateAndScorerType(TODAY, SCORER_TYPE))
			.willReturn(List.of(new ScoreSnapshot(300L, 0.0, 0.05)));

		// Act
		rankingCarryOverService.carryOver();

		// Assert — carry = 0.05 * 0.1 = 0.005, 내일로 전파됨
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<Long, Double>> carryCaptor = ArgumentCaptor.forClass(Map.class);
		verify(scoreCarryWritePort).upsertCarryScores(eq(TOMORROW), eq(SCORER_TYPE), carryCaptor.capture());
		assertThat(carryCaptor.getValue().get(300L)).isCloseTo(0.005, within(1e-10));
	}

}
