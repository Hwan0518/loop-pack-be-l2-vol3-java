package com.loopers.ranking.application.port.out;


import java.math.BigDecimal;


/**
 * ZSET 조회 결과 — 상품 ID + 점수 쌍
 */
public record ProductScoreEntry(Long productId, double score) {

}
