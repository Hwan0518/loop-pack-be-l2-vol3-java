package com.loopers.ranking.interfaces.web.response;


import com.loopers.ranking.application.dto.out.RankingItemOutDto;

import java.math.BigDecimal;


/**
 * 랭킹 항목 응답
 */
public record RankingItemResponse(int rank, Long productId, String name,
	BigDecimal price, String brandName, double score) {

	// 1. OutDto → Response 변환
	public static RankingItemResponse from(RankingItemOutDto outDto) {
		return new RankingItemResponse(
			outDto.rank(), outDto.productId(), outDto.name(),
			outDto.price(), outDto.brandName(), outDto.score());
	}

}
