package com.loopers.ranking.interfaces.web.response;


import com.loopers.ranking.application.dto.out.RankingPageOutDto;

import java.util.List;


/**
 * 랭킹 페이지 응답
 */
public record RankingPageResponse(List<RankingItemResponse> content, int page, int size, long totalElements) {

	// 1. OutDto → Response 변환
	public static RankingPageResponse from(RankingPageOutDto outDto) {
		List<RankingItemResponse> items = outDto.content().stream()
			.map(RankingItemResponse::from)
			.toList();
		return new RankingPageResponse(items, outDto.page(), outDto.size(), outDto.totalElements());
	}

}
