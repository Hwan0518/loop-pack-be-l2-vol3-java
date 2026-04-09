package com.loopers.ranking.application.port.out.client.catalog;


import java.math.BigDecimal;


/**
 * 랭킹 목록 조합용 상품 정보 (Cross-BC DTO)
 */
public record RankingProductInfo(Long productId, String name, BigDecimal price, String brandName) {

}
