package com.loopers.ranking.application.dto.out;


import com.loopers.ranking.application.port.out.ProductScoreEntry;

import java.util.List;


/**
 * 랭킹 ZSET 조회 결과 (Service → Facade 전달용)
 * - entries: 상품 ID + 점수 리스트
 * - totalElements: ZSET 전체 크기
 */
public record RankedResult(List<ProductScoreEntry> entries, long totalElements) {

}
