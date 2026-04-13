package com.loopers.ranking.application.dto.out;


/**
 * 랭킹 재계산 응답 OutDto
 */
public record AdminRankingRebuildOutDto(
	String from,
	String to,
	String scorerType,
	double carryOverWeight
) {
}
