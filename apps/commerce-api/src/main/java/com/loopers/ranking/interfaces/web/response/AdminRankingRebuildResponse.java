package com.loopers.ranking.interfaces.web.response;


/**
 * 랭킹 재계산 응답
 */
public record AdminRankingRebuildResponse(
	String status,
	String message,
	String from,
	String to,
	String scorerType,
	double carryOverWeight
) {

	public static AdminRankingRebuildResponse accepted(String from, String to, String scorerType, double carryOverWeight) {
		return new AdminRankingRebuildResponse(
			"ACCEPTED",
			"Rebuild job이 등록되었습니다. commerce-batch에서 job.name=rankingRebuildJob으로 실행해주세요.",
			from, to, scorerType, carryOverWeight
		);
	}

}
