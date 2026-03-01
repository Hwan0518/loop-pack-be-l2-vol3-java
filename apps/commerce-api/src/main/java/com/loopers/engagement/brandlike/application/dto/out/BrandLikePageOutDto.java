package com.loopers.engagement.brandlike.application.dto.out;

import com.loopers.engagement.brandlike.domain.model.BrandLike;
import com.loopers.engagement.brandlike.domain.repository.vo.PageResult;

import java.util.List;


/**
 * 브랜드 좋아요 페이지 결과 DTO
 */
public record BrandLikePageOutDto(List<BrandLikeOutDto> content, int page, int size, long totalElements) {

	// 1. PageResult를 DTO로 변환
	public static BrandLikePageOutDto from(PageResult<BrandLike> pageResult) {
		List<BrandLikeOutDto> content = pageResult.content().stream()
			.map(BrandLikeOutDto::from)
			.toList();
		return new BrandLikePageOutDto(content, pageResult.page(), pageResult.size(), pageResult.totalElements());
	}

}
