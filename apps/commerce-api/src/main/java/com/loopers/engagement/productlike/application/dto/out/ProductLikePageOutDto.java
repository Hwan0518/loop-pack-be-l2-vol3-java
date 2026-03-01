package com.loopers.engagement.productlike.application.dto.out;

import com.loopers.engagement.productlike.domain.model.ProductLike;
import com.loopers.engagement.productlike.domain.repository.vo.PageResult;

import java.util.List;


/**
 * 상품 좋아요 페이지 결과 DTO
 */
public record ProductLikePageOutDto(List<ProductLikeOutDto> content, int page, int size, long totalElements) {

	// 1. PageResult를 DTO로 변환
	public static ProductLikePageOutDto from(PageResult<ProductLike> pageResult) {
		List<ProductLikeOutDto> content = pageResult.content().stream()
			.map(ProductLikeOutDto::from)
			.toList();
		return new ProductLikePageOutDto(content, pageResult.page(), pageResult.size(), pageResult.totalElements());
	}

}
