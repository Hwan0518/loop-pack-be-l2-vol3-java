package com.loopers.ranking.application.facade;


import com.loopers.ranking.application.dto.out.RankedResult;
import com.loopers.ranking.application.dto.out.RankingPageOutDto;
import com.loopers.ranking.application.port.out.ProductScoreEntry;
import com.loopers.ranking.application.port.out.client.catalog.RankingProductInfo;
import com.loopers.ranking.application.service.RankingQueryService;
import com.loopers.ranking.application.service.RankingRankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
@DisplayName("RankingQueryFacade 단위 테스트")
class RankingQueryFacadeTest {

	@Mock
	private RankingQueryService rankingQueryService;

	@Mock
	private RankingRankService rankingRankService;

	private RankingQueryFacade rankingQueryFacade;


	@BeforeEach
	void setUp() {
		rankingQueryFacade = new RankingQueryFacade(rankingQueryService, rankingRankService);
	}


	@Nested
	@DisplayName("getRankings() 테스트")
	class GetRankingsTest {

		@Test
		@DisplayName("[getRankings()] 정상 조회 -> 상품 정보가 결합된 랭킹 목록 반환")
		void normalRankings() {
			// Arrange
			List<ProductScoreEntry> entries = List.of(
				new ProductScoreEntry(1L, 0.8),
				new ProductScoreEntry(2L, 0.6)
			);
			given(rankingQueryService.getRankedProductEntries(null, 0, 20))
				.willReturn(new RankedResult(entries, 2));

			given(rankingQueryService.readProducts(List.of(1L, 2L)))
				.willReturn(List.of(
					new RankingProductInfo(1L, "상품A", BigDecimal.valueOf(10000), "브랜드A"),
					new RankingProductInfo(2L, "상품B", BigDecimal.valueOf(20000), "브랜드B")
				));

			// Act
			RankingPageOutDto result = rankingQueryFacade.getRankings(null, 0, 20);

			// Assert
			assertThat(result.content()).hasSize(2);
			assertThat(result.content().get(0).rank()).isEqualTo(1);
			assertThat(result.content().get(0).name()).isEqualTo("상품A");
			assertThat(result.content().get(0).score()).isEqualTo(0.8);
			assertThat(result.content().get(1).rank()).isEqualTo(2);
			assertThat(result.totalElements()).isEqualTo(2);
		}

		@Test
		@DisplayName("[getRankings()] 빈 ZSET -> 빈 목록 반환")
		void emptyRankings() {
			// Arrange
			given(rankingQueryService.getRankedProductEntries(null, 0, 20))
				.willReturn(new RankedResult(List.of(), 0));

			// Act
			RankingPageOutDto result = rankingQueryFacade.getRankings(null, 0, 20);

			// Assert
			assertThat(result.content()).isEmpty();
			assertThat(result.totalElements()).isEqualTo(0);
		}

		@Test
		@DisplayName("[getRankings()] 삭제된 상품 -> skip하여 나머지만 반환")
		void deletedProductSkipped() {
			// Arrange
			List<ProductScoreEntry> entries = List.of(
				new ProductScoreEntry(1L, 0.8),
				new ProductScoreEntry(2L, 0.6),
				new ProductScoreEntry(3L, 0.4)
			);
			given(rankingQueryService.getRankedProductEntries(null, 0, 20))
				.willReturn(new RankedResult(entries, 3));

			// 상품 2L은 삭제되어 조회 결과에 미포함
			given(rankingQueryService.readProducts(List.of(1L, 2L, 3L)))
				.willReturn(List.of(
					new RankingProductInfo(1L, "상품A", BigDecimal.valueOf(10000), "브랜드A"),
					new RankingProductInfo(3L, "상품C", BigDecimal.valueOf(30000), "브랜드C")
				));

			// Act
			RankingPageOutDto result = rankingQueryFacade.getRankings(null, 0, 20);

			// Assert
			assertThat(result.content()).hasSize(2);
			assertThat(result.content().get(0).rank()).isEqualTo(1); // 1번째 위치
			assertThat(result.content().get(1).rank()).isEqualTo(3); // 3번째 위치 (2번째는 skip)
		}

		@Test
		@DisplayName("[getRankings()] page=1, size=10 -> rank가 11부터 시작")
		void secondPage() {
			// Arrange
			List<ProductScoreEntry> entries = List.of(
				new ProductScoreEntry(11L, 0.3)
			);
			given(rankingQueryService.getRankedProductEntries(null, 1, 10))
				.willReturn(new RankedResult(entries, 15));

			given(rankingQueryService.readProducts(List.of(11L)))
				.willReturn(List.of(
					new RankingProductInfo(11L, "상품K", BigDecimal.valueOf(5000), "브랜드K")
				));

			// Act
			RankingPageOutDto result = rankingQueryFacade.getRankings(null, 1, 10);

			// Assert
			assertThat(result.content().get(0).rank()).isEqualTo(11); // page=1, size=10 → baseRank=10
			assertThat(result.totalElements()).isEqualTo(15);
		}
	}


	@Nested
	@DisplayName("getProductRank() 테스트")
	class GetProductRankTest {

		@Test
		@DisplayName("[getProductRank()] RankingRankService에 위임하여 순위 반환")
		void delegatesToService() {
			// Arrange
			given(rankingRankService.getProductRank(null, 1L)).willReturn(3L);

			// Act
			Long rank = rankingQueryFacade.getProductRank(null, 1L);

			// Assert
			assertThat(rank).isEqualTo(3);
		}

		@Test
		@DisplayName("[getProductRank()] 미등록 상품 -> null")
		void notRanked() {
			// Arrange
			given(rankingRankService.getProductRank(null, 999L)).willReturn(null);

			// Act
			Long rank = rankingQueryFacade.getProductRank(null, 999L);

			// Assert
			assertThat(rank).isNull();
		}
	}

}
