package com.loopers.ranking.application.service;


import com.loopers.ranking.application.port.out.RankingRedisPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
@DisplayName("RankingRankService 단위 테스트")
class RankingRankServiceTest {

	@Mock
	private RankingRedisPort rankingRedisPort;

	private RankingRankService rankingRankService;

	private static final String TODAY = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);


	@BeforeEach
	void setUp() {
		rankingRankService = new RankingRankService(rankingRedisPort);
	}


	@Nested
	@DisplayName("getProductRank() 테스트")
	class GetProductRankTest {

		@Test
		@DisplayName("[getProductRank()] ZREVRANK=0 -> 1. 0-based를 1-based로 변환")
		void firstRank() {
			// Arrange
			given(rankingRedisPort.getReversedRank(TODAY, 1L)).willReturn(0L);

			// Act
			Long rank = rankingRankService.getProductRank(null, 1L);

			// Assert
			assertThat(rank).isEqualTo(1);
		}

		@Test
		@DisplayName("[getProductRank()] ZREVRANK=4 -> 5. 5위 상품")
		void fifthRank() {
			// Arrange
			given(rankingRedisPort.getReversedRank(TODAY, 5L)).willReturn(4L);

			// Act
			Long rank = rankingRankService.getProductRank(null, 5L);

			// Assert
			assertThat(rank).isEqualTo(5);
		}

		@Test
		@DisplayName("[getProductRank()] ZREVRANK=null -> null. 랭킹 미등록 상품")
		void notRanked() {
			// Arrange
			given(rankingRedisPort.getReversedRank(TODAY, 999L)).willReturn(null);

			// Act
			Long rank = rankingRankService.getProductRank(null, 999L);

			// Assert
			assertThat(rank).isNull();
		}

		@Test
		@DisplayName("[getProductRank()] date 지정 -> 해당 날짜 키로 조회")
		void specificDate() {
			// Arrange
			given(rankingRedisPort.getReversedRank("20260407", 1L)).willReturn(2L);

			// Act
			Long rank = rankingRankService.getProductRank("20260407", 1L);

			// Assert
			assertThat(rank).isEqualTo(3);
		}
	}

}
