package com.loopers.batch.job.ranking.scorer;


/**
 * 랭킹 점수 계산기 인터페이스 (commerce-batch 전용 — rebuild/reconcile 에서 사용)
 * - commerce-streamer 의 RankingScorer 와 동일 계약
 * - 공유 모듈 추출 전까지 batch 에 별도 정의
 */
public interface RankingScorer {

	/**
	 * 일간 점수 계산
	 * @return 0~1 범위의 종합 점수
	 */
	double calculateScore(long view, long like, long order);

}
