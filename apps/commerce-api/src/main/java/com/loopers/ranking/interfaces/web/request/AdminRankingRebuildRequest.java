package com.loopers.ranking.interfaces.web.request;


import jakarta.validation.constraints.NotBlank;


/**
 * 랭킹 재계산 요청
 */
public record AdminRankingRebuildRequest(
	@NotBlank String from,
	@NotBlank String to,
	String scorerType,
	Double carryOverWeight
) {
}
