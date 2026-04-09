package com.loopers.ranking.application.port.out;


/**
 * 랭킹 점수 계산기 인터페이스
 * - Scorer 구현체를 교체하여 점수 정책을 변경할 수 있음
 * - 기본: SaturationScorer (saturation 함수 기반)
 */
public interface RankingScorer {

	/**
	 * 일간 점수 계산
	 * @param view 일간 조회수
	 * @param like 일간 좋아요수
	 * @param order 일간 주문수량
	 * @return 0~1 범위의 종합 점수
	 */
	double calculateScore(long view, long like, long order);

}
