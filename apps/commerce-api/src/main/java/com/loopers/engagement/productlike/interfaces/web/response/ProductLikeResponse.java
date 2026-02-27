package com.loopers.engagement.productlike.interfaces.web.response;

import com.loopers.engagement.productlike.application.dto.out.ProductLikeOutDto;

import java.time.LocalDateTime;


/**
 * 상품 좋아요 응답
 */
public record ProductLikeResponse(Long id, Long userId, Long targetId, LocalDateTime createdAt) {

	// 1. OutDto -> Response 변환
	public static ProductLikeResponse from(ProductLikeOutDto outDto) {
		return new ProductLikeResponse(outDto.id(), outDto.userId(), outDto.targetId(), outDto.createdAt());
	}

}
