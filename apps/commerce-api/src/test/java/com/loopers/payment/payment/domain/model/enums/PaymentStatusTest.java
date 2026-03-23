package com.loopers.payment.payment.domain.model.enums;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


@DisplayName("PaymentStatus 테스트")
class PaymentStatusTest {

	@Test
	@DisplayName("[canTransitionTo()] REQUESTED -> SUCCESS 전이 가능")
	void requestedToSuccess() {
		// Act & Assert
		assertThat(PaymentStatus.REQUESTED.canTransitionTo(PaymentStatus.SUCCESS)).isTrue();
	}

	@Test
	@DisplayName("[canTransitionTo()] REQUESTED -> FAILED 전이 가능")
	void requestedToFailed() {
		// Act & Assert
		assertThat(PaymentStatus.REQUESTED.canTransitionTo(PaymentStatus.FAILED)).isTrue();
	}

	@Test
	@DisplayName("[canTransitionTo()] REQUESTED -> REQUESTED 전이 불가")
	void requestedToRequested() {
		// Act & Assert
		assertThat(PaymentStatus.REQUESTED.canTransitionTo(PaymentStatus.REQUESTED)).isFalse();
	}

	@Test
	@DisplayName("[canTransitionTo()] SUCCESS -> SUCCESS 전이 불가. 최종 상태")
	void successToSuccess() {
		// Act & Assert
		assertThat(PaymentStatus.SUCCESS.canTransitionTo(PaymentStatus.SUCCESS)).isFalse();
	}

	@Test
	@DisplayName("[canTransitionTo()] SUCCESS -> FAILED 전이 불가. 최종 상태")
	void successToFailed() {
		// Act & Assert
		assertThat(PaymentStatus.SUCCESS.canTransitionTo(PaymentStatus.FAILED)).isFalse();
	}

	@Test
	@DisplayName("[canTransitionTo()] FAILED -> SUCCESS 전이 불가. 최종 상태")
	void failedToSuccess() {
		// Act & Assert
		assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.SUCCESS)).isFalse();
	}

	@Test
	@DisplayName("[canTransitionTo()] FAILED -> FAILED 전이 불가. 최종 상태")
	void failedToFailed() {
		// Act & Assert
		assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.FAILED)).isFalse();
	}

	@Test
	@DisplayName("[PaymentStatus] enum 상수 개수가 3개임을 보장")
	void enumConstantCount() {
		// Assert
		assertThat(PaymentStatus.values()).hasSize(3);
	}

}
