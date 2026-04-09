package com.loopers.catalog.product.infrastructure.acl.ranking;


import com.loopers.catalog.product.application.port.out.client.ranking.ProductRankingReader;
import com.loopers.ranking.application.facade.RankingRankFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


@Component
@RequiredArgsConstructor
public class ProductRankingReaderImpl implements ProductRankingReader {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

	// facade (provider BC — RankingRankFacade는 catalog와 순환 없음)
	private final RankingRankFacade rankingRankFacade;


	/**
	 * catalog → ranking ACL 어댑터
	 * - RankingRankFacade.getProductRank() 호출 위임
	 * - 순환참조 없음: RankingRankFacade → RankingRankService → RankingRedisPort
	 *
	 * 1. 오늘의 랭킹에서 해당 상품의 순위 조회
	 */

	// 1. 오늘의 랭킹에서 해당 상품의 순위 조회
	@Override
	public Long getProductRank(Long productId) {
		String todayDateStr = LocalDate.now().format(DATE_FORMAT);
		return rankingRankFacade.getProductRank(todayDateStr, productId);
	}

}
