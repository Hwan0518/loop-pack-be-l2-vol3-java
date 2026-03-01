package com.loopers.engagement.brandlike.interfaces.web.response;

import com.loopers.engagement.brandlike.application.dto.out.BrandLikeOutDto;

import java.time.LocalDateTime;


/**
 * 브랜드 좋아요 응답
 */
public record BrandLikeResponse(Long id, Long userId, Long targetId, LocalDateTime createdAt) {

	// 1. OutDto -> Response 변환
	public static BrandLikeResponse from(BrandLikeOutDto outDto) {
		return new BrandLikeResponse(outDto.id(), outDto.userId(), outDto.targetId(), outDto.createdAt());
	}

}
