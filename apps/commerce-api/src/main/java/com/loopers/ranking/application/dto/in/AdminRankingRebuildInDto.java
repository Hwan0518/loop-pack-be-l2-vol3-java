package com.loopers.ranking.application.dto.in;


/**
 * 랭킹 재계산 요청 InDto
 */
public record AdminRankingRebuildInDto(
	String from,
	String to,
	String scorerType,
	double carryOverWeight
) {
}
