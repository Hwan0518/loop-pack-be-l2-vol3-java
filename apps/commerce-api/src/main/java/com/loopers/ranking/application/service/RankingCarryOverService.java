package com.loopers.ranking.application.service;


import com.loopers.ranking.application.port.out.RankingRedisPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


@Service
@RequiredArgsConstructor
@Slf4j
public class RankingCarryOverService {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
	private static final double CARRY_OVER_WEIGHT = 0.1;
	private static final Duration TTL = Duration.ofDays(2);

	// port
	private final RankingRedisPort rankingRedisPort;


	/**
	 * 랭킹 콜드 스타트 서비스
	 * - 전날 점수의 10%를 다음날 키로 carry-over
	 *
	 * 1. carry-over 실행 (ZUNIONSTORE + EXPIRE)
	 */

	// 1. carry-over 실행
	public void carryOver() {
		LocalDate today = LocalDate.now();
		LocalDate tomorrow = today.plusDays(1);

		String todayStr = today.format(DATE_FORMAT);
		String tomorrowStr = tomorrow.format(DATE_FORMAT);

		log.info("[RankingCarryOver] carry-over 시작: {} → {} (weight={})", todayStr, tomorrowStr, CARRY_OVER_WEIGHT);

		// ZUNIONSTORE + EXPIRE
		rankingRedisPort.carryOver(todayStr, tomorrowStr, CARRY_OVER_WEIGHT);
		rankingRedisPort.setTtl(tomorrowStr, TTL);

		log.info("[RankingCarryOver] carry-over 완료: {}", tomorrowStr);
	}

}
