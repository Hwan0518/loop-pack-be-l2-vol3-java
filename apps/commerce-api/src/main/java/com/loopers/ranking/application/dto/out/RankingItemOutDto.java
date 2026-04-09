package com.loopers.ranking.application.dto.out;


import java.math.BigDecimal;


/**
 * 랭킹 항목 조회 결과 DTO
 * - rank: 순위 (1-based)
 * - productId: 상품 ID
 * - name: 상품명
 * - price: 가격
 * - brandName: 브랜드명
 * - score: 랭킹 점수
 */
public record RankingItemOutDto(int rank, Long productId, String name,
	BigDecimal price, String brandName, double score) {

}
