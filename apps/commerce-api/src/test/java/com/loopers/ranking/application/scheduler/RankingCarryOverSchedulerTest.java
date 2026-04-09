package com.loopers.ranking.application.scheduler;


import com.loopers.ranking.application.service.RankingCarryOverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("RankingCarryOverScheduler 단위 테스트")
class RankingCarryOverSchedulerTest {

	@Mock
	private RankingCarryOverService rankingCarryOverService;

	private RankingCarryOverScheduler scheduler;


	@BeforeEach
	void setUp() {
		scheduler = new RankingCarryOverScheduler(rankingCarryOverService);
	}


	@Nested
	@DisplayName("execute() 테스트")
	class ExecuteTest {

		@Test
		@DisplayName("[execute()] 정상 실행 -> RankingCarryOverService.carryOver() 1회 호출")
		void normalExecution() {
			// Act
			scheduler.execute();

			// Assert
			verify(rankingCarryOverService).carryOver();
		}

		@Test
		@DisplayName("[execute()] carryOver 예외 -> 예외 전파하지 않음 (로깅만). 스케줄러 중단 방지")
		void exceptionHandled() {
			// Arrange
			doThrow(new RuntimeException("Redis 연결 실패")).when(rankingCarryOverService).carryOver();

			// Act — 예외 없이 정상 종료
			scheduler.execute();

			// Assert
			verify(rankingCarryOverService).carryOver();
		}
	}

}
