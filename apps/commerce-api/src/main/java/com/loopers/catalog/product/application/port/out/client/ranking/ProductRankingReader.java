package com.loopers.catalog.product.application.port.out.client.ranking;


/**
 * catalog → ranking 상품 순위 조회 포트
 */
public interface ProductRankingReader {

	/**
	 * 오늘의 랭킹에서 해당 상품의 순위 조회
	 * @return 1-based 순위 (null이면 미등록)
	 */
	Long getProductRank(Long productId);

}
