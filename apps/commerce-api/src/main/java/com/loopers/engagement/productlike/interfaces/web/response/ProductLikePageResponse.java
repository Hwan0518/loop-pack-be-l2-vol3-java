package com.loopers.engagement.productlike.interfaces.web.response;

import com.loopers.engagement.productlike.application.dto.out.ProductLikePageOutDto;

import java.util.List;


/**
 * 상품 좋아요 페이지 응답
 */
public record ProductLikePageResponse(List<ProductLikeResponse> content, int page, int size, long totalElements) {

	// 1. OutDto -> Response 변환
	public static ProductLikePageResponse from(ProductLikePageOutDto outDto) {
		List<ProductLikeResponse> content = outDto.content().stream()
			.map(ProductLikeResponse::from)
			.toList();
		return new ProductLikePageResponse(content, outDto.page(), outDto.size(), outDto.totalElements());
	}

}
