package com.loopers.ranking.application.service;


import com.loopers.ranking.application.dto.out.RankedResult;
import com.loopers.ranking.application.port.out.ProductScoreEntry;
import com.loopers.ranking.application.port.out.RankingRedisPort;
import com.loopers.ranking.application.port.out.client.catalog.RankingProductReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("RankingQueryService 단위 테스트")
class RankingQueryServiceTest {

	@Mock
	private RankingRedisPort rankingRedisPort;

	@Mock
	private RankingProductReader rankingProductReader;

	private RankingQueryService rankingQueryService;

	private static final String TODAY = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);


	@BeforeEach
	void setUp() {
		rankingQueryService = new RankingQueryService(rankingRedisPort, rankingProductReader);
	}


	@Nested
	@DisplayName("getRankedProductEntries() 테스트")
	class GetRankedProductEntriesTest {

		@Test
		@DisplayName("[getRankedProductEntries()] page=0, size=20 -> offset=0, count=20으로 조회")
		void firstPage() {
			// Arrange
			List<ProductScoreEntry> entries = List.of(
				new ProductScoreEntry(1L, 0.8),
				new ProductScoreEntry(2L, 0.6)
			);
			given(rankingRedisPort.getTopProducts(TODAY, 0, 20)).willReturn(entries);
			given(rankingRedisPort.getZSetSize(TODAY)).willReturn(50L);

			// Act
			RankedResult result = rankingQueryService.getRankedProductEntries(null, 0, 20);

			// Assert
			assertThat(result.entries()).hasSize(2);
			assertThat(result.totalElements()).isEqualTo(50);
			verify(rankingRedisPort).getTopProducts(TODAY, 0, 20);
		}

		@Test
		@DisplayName("[getRankedProductEntries()] page=2, size=10 -> offset=20, count=10으로 조회")
		void thirdPage() {
			// Arrange
			given(rankingRedisPort.getTopProducts(TODAY, 20, 10)).willReturn(List.of());
			given(rankingRedisPort.getZSetSize(TODAY)).willReturn(15L);

			// Act
			RankedResult result = rankingQueryService.getRankedProductEntries(null, 2, 10);

			// Assert
			assertThat(result.entries()).isEmpty();
			assertThat(result.totalElements()).isEqualTo(15);
		}

		@Test
		@DisplayName("[getRankedProductEntries()] date 지정 -> 해당 날짜 키로 조회")
		void specificDate() {
			// Arrange
			given(rankingRedisPort.getTopProducts("20260407", 0, 20)).willReturn(List.of());
			given(rankingRedisPort.getZSetSize("20260407")).willReturn(0L);

			// Act
			RankedResult result = rankingQueryService.getRankedProductEntries("20260407", 0, 20);

			// Assert
			verify(rankingRedisPort).getTopProducts("20260407", 0, 20);
		}
	}


}
