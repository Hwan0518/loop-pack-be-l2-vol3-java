package com.loopers.ranking.application.service;


import com.loopers.ranking.application.port.out.RankingRedisPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


@Service
@RequiredArgsConstructor
public class RankingRankService {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

	// port
	private final RankingRedisPort rankingRedisPort;


	/**
	 * 랭킹 순위 조회 서비스 (rank 전용 — 순환참조 방지를 위해 분리)
	 * - RankingRedisPort만 의존 → catalog BC와 순환 없음
	 *
	 * 1. 특정 상품의 랭킹 순위 조회 (1-based, 미등록이면 null)
	 */

	// 1. 특정 상품의 랭킹 순위 조회
	@Transactional(readOnly = true)
	public Long getProductRank(String dateStr, Long productId) {
		String resolvedDate = resolveDate(dateStr);
		Long zeroBasedRank = rankingRedisPort.getReversedRank(resolvedDate, productId);

		if (zeroBasedRank == null) return null;
		return zeroBasedRank + 1; // 0-based → 1-based
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
