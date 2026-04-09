package com.loopers.ranking.application.facade;


import com.loopers.ranking.application.service.RankingRankService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 랭킹 순위 조회 파사드 (rank 전용 — 순환참조 방지를 위해 분리)
 * - catalog BC의 ProductRankingReader ACL이 호출하는 진입점
 * - RankingRankService만 의존 → catalog와 순환 없음
 *
 * 1. 특정 상품 랭킹 순위 조회
 */
@Service
@RequiredArgsConstructor
public class RankingRankFacade {

	// service
	private final RankingRankService rankingRankService;


	// 1. 특정 상품 랭킹 순위 조회
	@Transactional(readOnly = true)
	public Long getProductRank(String dateStr, Long productId) {
		return rankingRankService.getProductRank(dateStr, productId);
	}

}
