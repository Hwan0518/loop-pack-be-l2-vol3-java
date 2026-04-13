package com.loopers.ranking.application.dto;


import java.time.LocalDate;


/**
 * 랭킹 일간 집계 키 (배치 내 delta 합산용)
 * - statDate: envelope.occurredAt 기준 KST LocalDate
 * - productId: 상품 ID
 * - 같은 배치 안에 다른 날짜 이벤트가 섞일 수 있으므로 productId 단일 키 대신 (statDate, productId) 합성 키를 사용한다
 */
public record RankingDailyKey(
	LocalDate statDate,
	Long productId
) {
}
