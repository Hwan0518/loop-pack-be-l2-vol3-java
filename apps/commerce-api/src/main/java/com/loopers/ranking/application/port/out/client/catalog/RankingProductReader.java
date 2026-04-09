package com.loopers.ranking.application.port.out.client.catalog;


import java.util.List;


/**
 * 랭킹 → catalog 상품 정보 조회 포트
 */
public interface RankingProductReader {

	/**
	 * 상품 ID 목록으로 활성 상품 정보 일괄 조회
	 */
	List<RankingProductInfo> readProducts(List<Long> productIds);

}
