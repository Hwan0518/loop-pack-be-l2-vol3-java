package com.loopers.ranking.application.service;


import com.loopers.ranking.application.port.out.RankingRedisPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("RankingCarryOverService 단위 테스트")
class RankingCarryOverServiceTest {

	@Mock
	private RankingRedisPort rankingRedisPort;

	private RankingCarryOverService rankingCarryOverService;

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;


	@BeforeEach
	void setUp() {
		rankingCarryOverService = new RankingCarryOverService(rankingRedisPort);
	}


	@Test
	@DisplayName("[carryOver()] carry-over 실행 -> ZUNIONSTORE + EXPIRE 호출. today→tomorrow weight=0.1")
	void carryOverExecutes() {
		// Arrange
		String todayStr = LocalDate.now().format(DATE_FORMAT);
		String tomorrowStr = LocalDate.now().plusDays(1).format(DATE_FORMAT);

		// Act
		rankingCarryOverService.carryOver();

		// Assert
		verify(rankingRedisPort).carryOver(todayStr, tomorrowStr, 0.1);
		verify(rankingRedisPort).setTtl(tomorrowStr, Duration.ofDays(2));
	}

}
