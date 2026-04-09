package com.loopers.ranking.application.service;


import com.loopers.ranking.application.dto.out.RankedResult;
import com.loopers.ranking.application.port.out.ProductScoreEntry;
import com.loopers.ranking.application.port.out.RankingRedisPort;
import com.loopers.ranking.application.port.out.client.catalog.RankingProductInfo;
import com.loopers.ranking.application.port.out.client.catalog.RankingProductReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;


@Service
@RequiredArgsConstructor
public class RankingQueryService {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

	// port
	private final RankingRedisPort rankingRedisPort;
	// port (cross-BC)
	private final RankingProductReader rankingProductReader;


	/**
	 * 랭킹 조회 서비스 (목록 조회 + Cross-BC 상품 정보)
	 * - rank 단건 조회는 RankingRankService로 분리 (순환참조 방지)
	 *
	 * 1. 랭킹 상위 상품 조회 (ZREVRANGE + ZCARD)
	 * 2. 상품 정보 일괄 조회 (Cross-BC → catalog)
	 */

	// 1. 랭킹 상위 상품 조회 (ZREVRANGE + ZCARD)
	@Transactional(readOnly = true)
	public RankedResult getRankedProductEntries(String dateStr, int page, int size) {
		String resolvedDate = resolveDate(dateStr);
		long offset = (long) page * size;

		List<ProductScoreEntry> entries = rankingRedisPort.getTopProducts(resolvedDate, offset, size);
		long totalElements = rankingRedisPort.getZSetSize(resolvedDate);

		return new RankedResult(entries, totalElements);
	}


	// 2. 상품 정보 일괄 조회 (Cross-BC → catalog)
	@Transactional(readOnly = true)
	public List<RankingProductInfo> readProducts(List<Long> productIds) {
		return rankingProductReader.readProducts(productIds);
	}


	/**
	 * private method
	 * - 날짜 문자열 해석 (null → 오늘)
	 */

	// 날짜 문자열 해석 (null → 오늘)
	private String resolveDate(String dateStr) {
		if (dateStr == null || dateStr.isBlank()) {
			return LocalDate.now().format(DATE_FORMAT);
		}
		return dateStr;
	}


}
