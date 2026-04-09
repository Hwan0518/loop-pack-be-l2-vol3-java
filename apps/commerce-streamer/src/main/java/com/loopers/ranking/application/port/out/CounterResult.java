package com.loopers.ranking.application.port.out;


/**
 * 카운터 HINCRBY 결과 — 이전/이후 카운트 쌍
 * - saturation delta 계산에 사용
 */
public record CounterResult(
	long oldView, long newView,
	long oldLike, long newLike,
	long oldOrder, long newOrder
) {

}
