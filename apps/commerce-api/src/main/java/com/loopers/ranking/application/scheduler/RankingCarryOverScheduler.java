package com.loopers.ranking.application.scheduler;


import com.loopers.ranking.application.service.RankingCarryOverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
@ConditionalOnProperty(name = "ranking.carryover.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RankingCarryOverScheduler {

	// service
	private final RankingCarryOverService rankingCarryOverService;


	/**
	 * 랭킹 콜드 스타트 스케줄러
	 * - 매일 23:50에 carry-over 실행
	 * - 전날 점수의 10%를 다음날 키로 복사
	 *
	 * 1. carry-over 스케줄 실행
	 */

	// 1. carry-over 스케줄 실행 (매일 23:50)
	@Scheduled(cron = "0 50 23 * * *")
	public void execute() {
		try {
			rankingCarryOverService.carryOver();
		} catch (Exception e) {
			log.error("[RankingCarryOverScheduler] carry-over 실패", e);
		}
	}

}
