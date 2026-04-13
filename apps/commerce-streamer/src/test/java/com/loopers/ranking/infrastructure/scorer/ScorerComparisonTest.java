package com.loopers.ranking.infrastructure.scorer;


import com.loopers.ranking.application.port.out.RankingScorer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;


/**
 * Scorer 3종 비교 테스트
 * - SaturationScorer: 종합 인기도 (포화 함수, 절대량 기반)
 * - LinearScorer: 누적량 비례 (선형 가중합)
 * - ConversionScorer: 전환율 기반 (조회 대비 구매/좋아요 비율)
 *
 * 목적: 동일 입력에 대해 3개 Scorer가 서로 다른 순위를 산출함을 검증
 */
@DisplayName("Scorer 3종 비교 테스트")
class ScorerComparisonTest {

	private RankingScorer saturationScorer;
	private RankingScorer linearScorer;
	private RankingScorer conversionScorer;


	@BeforeEach
	void setUp() {
		saturationScorer = new SaturationScorer();
		linearScorer = new LinearScorer();
		conversionScorer = new ConversionScorer();
	}


	@Nested
	@DisplayName("기본 동작 검증")
	class BasicBehaviorTest {

		@Test
		@DisplayName("[전체 Scorer] 모든 카운트 0 -> 0.0. 이벤트 없으면 점수 0")
		void allZeroScores() {
			// Assert
			assertThat(saturationScorer.calculateScore(0, 0, 0)).isEqualTo(0.0);
			assertThat(linearScorer.calculateScore(0, 0, 0)).isEqualTo(0.0);
			assertThat(conversionScorer.calculateScore(0, 0, 0)).isEqualTo(0.0);
		}

		@Test
		@DisplayName("[전체 Scorer] 양수 입력 -> 양수 점수. 이벤트가 있으면 점수 > 0")
		void positiveScores() {
			// Act
			double satScore = saturationScorer.calculateScore(10, 5, 2);
			double linScore = linearScorer.calculateScore(10, 5, 2);
			double conScore = conversionScorer.calculateScore(10, 5, 2);

			// Assert
			assertThat(satScore).isGreaterThan(0.0);
			assertThat(linScore).isGreaterThan(0.0);
			assertThat(conScore).isGreaterThan(0.0);
		}
	}


	@Nested
	@DisplayName("비즈니스 시나리오 비교")
	class BusinessScenarioTest {

		@Test
		@DisplayName("[인기 상품 vs 숨은 보석] 상품A(view=1000, order=5) vs 상품B(view=50, order=4). " +
			"SaturationScorer는 A 우세, ConversionScorer는 B 우세")
		void popularVsHiddenGem() {
			// Arrange — 상품A: 노출 많지만 전환율 0.5%, 상품B: 노출 적지만 전환율 8%
			double satA = saturationScorer.calculateScore(1000, 10, 5);
			double satB = saturationScorer.calculateScore(50, 8, 4);
			double conA = conversionScorer.calculateScore(1000, 10, 5);
			double conB = conversionScorer.calculateScore(50, 8, 4);

			// Assert — SaturationScorer: A > B (절대량 기반)
			assertThat(satA).isGreaterThan(satB);
			// Assert — ConversionScorer: B > A (전환율 기반)
			assertThat(conB).isGreaterThan(conA);
		}

		@Test
		@DisplayName("[대량 조회 vs 소량 고전환] 상품A(view=500, like=20, order=2) vs 상품B(view=30, like=10, order=3). " +
			"SaturationScorer는 A 우세, ConversionScorer는 B 우세")
		void highTrafficVsHighConversion() {
			// Arrange
			double satA = saturationScorer.calculateScore(500, 20, 2);
			double satB = saturationScorer.calculateScore(30, 10, 3);
			double conA = conversionScorer.calculateScore(500, 20, 2);
			double conB = conversionScorer.calculateScore(30, 10, 3);

			// Assert — Saturation: A > B (view 500 >> 30)
			assertThat(satA).isGreaterThan(satB);
			// Assert — Conversion: B > A (order/view = 10% >> 0.4%)
			assertThat(conB).isGreaterThan(conA);
		}

		@Test
		@DisplayName("[주문 1건 > 좋아요 3건] 모든 Scorer에서 주문의 가중치가 좋아요보다 높음")
		void orderWeightHigherThanLike() {
			// Arrange — 동일 view 기준으로 비교
			double satOrder = saturationScorer.calculateScore(100, 0, 1);
			double satLike = saturationScorer.calculateScore(100, 3, 0);
			double linOrder = linearScorer.calculateScore(100, 0, 1);
			double linLike = linearScorer.calculateScore(100, 3, 0);
			double conOrder = conversionScorer.calculateScore(100, 0, 1);
			double conLike = conversionScorer.calculateScore(100, 3, 0);

			// Assert — 모든 Scorer에서 주문 1건 > 좋아요 3건
			assertThat(satOrder).isGreaterThan(satLike);
			assertThat(linOrder).isGreaterThan(linLike);
			assertThat(conOrder).isGreaterThan(conLike);
		}
	}


	@Nested
	@DisplayName("ConversionScorer 신뢰도 검증")
	class ConversionConfidenceTest {

		@Test
		@DisplayName("[ConversionScorer] view=1, order=1 (100% 전환율) vs view=100, order=10 (10% 전환율). " +
			"신뢰도 보정으로 view=100이 더 높은 점수")
		void confidenceProtectsLowViewCount() {
			// Arrange — view=1은 전환율 100%이지만 신뢰도 극히 낮음
			double lowView = conversionScorer.calculateScore(1, 1, 1);
			double highView = conversionScorer.calculateScore(100, 10, 10);

			// Assert — 신뢰도 보정으로 view=100이 더 높은 점수
			assertThat(highView).isGreaterThan(lowView);
		}

		@Test
		@DisplayName("[ConversionScorer] view=0 -> 0.0. 조회가 없으면 전환율 계산 불가")
		void zeroViewReturnsZero() {
			// Act & Assert — view=0이면 전환율 무의미 → 0점
			assertThat(conversionScorer.calculateScore(0, 5, 3)).isEqualTo(0.0);
		}
	}


	@Nested
	@DisplayName("Scorer 교체 가능성 검증")
	class ScorerSwapTest {

		@Test
		@DisplayName("[RankingScorer 인터페이스] 3개 구현체 모두 동일 인터페이스. 교체 가능")
		void allImplementSameInterface() {
			// Assert — 모두 RankingScorer 타입
			assertThat(saturationScorer).isInstanceOf(RankingScorer.class);
			assertThat(linearScorer).isInstanceOf(RankingScorer.class);
			assertThat(conversionScorer).isInstanceOf(RankingScorer.class);
		}

		@Test
		@DisplayName("[동일 입력, 다른 점수] view=200, like=15, order=5 -> 3개 Scorer가 서로 다른 점수 산출")
		void differentScoresForSameInput() {
			// Act
			double sat = saturationScorer.calculateScore(200, 15, 5);
			double lin = linearScorer.calculateScore(200, 15, 5);
			double con = conversionScorer.calculateScore(200, 15, 5);

			// Assert — 3개 모두 다른 점수
			assertThat(sat).isNotEqualTo(lin);
			assertThat(sat).isNotEqualTo(con);
			assertThat(lin).isNotEqualTo(con);
		}
	}

}
