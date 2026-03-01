package com.loopers.engagement.brandlike.interfaces.web.response;

import com.loopers.engagement.brandlike.application.dto.out.BrandLikePageOutDto;

import java.util.List;


/**
 * 브랜드 좋아요 페이지 응답
 */
public record BrandLikePageResponse(List<BrandLikeResponse> content, int page, int size, long totalElements) {

	// 1. OutDto -> Response 변환
	public static BrandLikePageResponse from(BrandLikePageOutDto outDto) {
		List<BrandLikeResponse> content = outDto.content().stream()
			.map(BrandLikeResponse::from)
			.toList();
		return new BrandLikePageResponse(content, outDto.page(), outDto.size(), outDto.totalElements());
	}

}
