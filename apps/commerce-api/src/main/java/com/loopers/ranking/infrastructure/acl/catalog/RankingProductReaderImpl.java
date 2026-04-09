package com.loopers.ranking.infrastructure.acl.catalog;


import com.loopers.catalog.product.application.facade.ProductQueryFacade;
import com.loopers.catalog.product.application.port.out.cache.dto.ProductCacheDto;
import com.loopers.ranking.application.port.out.client.catalog.RankingProductInfo;
import com.loopers.ranking.application.port.out.client.catalog.RankingProductReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@RequiredArgsConstructor
public class RankingProductReaderImpl implements RankingProductReader {

	// facade (provider BC)
	private final ProductQueryFacade productQueryFacade;


	/**
	 * 랭킹 → catalog ACL 어댑터
	 * - ProductQueryFacade.findCacheDtosByIds() 호출 → RankingProductInfo 변환
	 *
	 * 1. 상품 ID 목록으로 활성 상품 정보 일괄 조회
	 */

	// 1. 상품 ID 목록으로 활성 상품 정보 일괄 조회
	@Override
	public List<RankingProductInfo> readProducts(List<Long> productIds) {
		List<ProductCacheDto> cacheDtos = productQueryFacade.findCacheDtosByIds(productIds);

		return cacheDtos.stream()
			.map(dto -> new RankingProductInfo(dto.id(), dto.name(), dto.price(), dto.brandName()))
			.toList();
	}

}
