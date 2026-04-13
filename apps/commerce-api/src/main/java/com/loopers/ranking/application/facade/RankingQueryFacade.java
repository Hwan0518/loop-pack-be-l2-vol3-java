package com.loopers.ranking.application.facade;


import com.loopers.ranking.application.dto.out.RankedResult;
import com.loopers.ranking.application.dto.out.RankingItemOutDto;
import com.loopers.ranking.application.dto.out.RankingPageOutDto;
import com.loopers.ranking.application.port.out.ProductScoreEntry;
import com.loopers.ranking.application.port.out.client.catalog.RankingProductInfo;
import com.loopers.ranking.application.service.RankingQueryService;
import com.loopers.ranking.application.service.RankingRankService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class RankingQueryFacade {

	// service
	private final RankingQueryService rankingQueryService;
	private final RankingRankService rankingRankService;


	/**
	 * 랭킹 조회 파사드 (목록 조회 전용)
	 * - rank 단건 조회는 RankingRankFacade로 분리 (순환참조 방지)
	 *
	 * 1. 랭킹 목록 조회 (ZSET 조회 + 상품 정보 조합)
	 * 2. 특정 상품 랭킹 순위 조회 (RankingRankService에 위임)
	 */

	// 1. 랭킹 목록 조회
	@Transactional(readOnly = true)
	public RankingPageOutDto getRankings(String dateStr, int page, int size) {

		// ZSET 조회 (productId + score 리스트 + totalElements)
		RankedResult rankedResult = rankingQueryService.getRankedProductEntries(dateStr, page, size);
		List<ProductScoreEntry> entries = rankedResult.entries();

		if (entries.isEmpty()) {
			return new RankingPageOutDto(List.of(), page, size, rankedResult.totalElements());
		}

		// 상품 ID 추출
		List<Long> productIds = entries.stream()
			.map(ProductScoreEntry::productId)
			.toList();

		// Cross-BC: 상품 정보 일괄 조회 (Service 경유)
		List<RankingProductInfo> productInfos = rankingQueryService.readProducts(productIds);
		Map<Long, RankingProductInfo> infoMap = productInfos.stream()
			.collect(Collectors.toMap(RankingProductInfo::productId, Function.identity()));

		// 순서 유지하며 join (삭제/비활성 상품은 skip)
		int baseRank = page * size;
		List<RankingItemOutDto> items = new ArrayList<>();
		int index = 0;

		for (ProductScoreEntry entry : entries) {
			RankingProductInfo info = infoMap.get(entry.productId());
			if (info == null) {
				index++;
				continue; // 삭제된 상품 skip
			}

			items.add(new RankingItemOutDto(
				baseRank + index + 1, // 1-based rank
				entry.productId(),
				info.name(),
				info.price(),
				info.brandName(),
				entry.score()
			));
			index++;
		}

		return new RankingPageOutDto(items, page, size, rankedResult.totalElements());
	}


	// 2. 특정 상품 랭킹 순위 조회 (RankingRankService에 위임)
	@Transactional(readOnly = true)
	public Long getProductRank(String dateStr, Long productId) {
		return rankingRankService.getProductRank(dateStr, productId);
	}

}
