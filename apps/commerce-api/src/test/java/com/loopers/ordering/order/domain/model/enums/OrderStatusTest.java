package com.loopers.ordering.order.domain.model.enums;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;


@DisplayName("OrderStatus 도메인 enum 테스트")
class OrderStatusTest {

	@Nested
	@DisplayName("canTransitionTo() — 허용된 전이")
	class AllowedTransitionsTest {

		@ParameterizedTest(name = "[{index}] {0} → {1} = true")
		@MethodSource("allowedTransitions")
		@DisplayName("[canTransitionTo()] 허용된 상태 전이 -> true")
		void allowedTransitionReturnsTrue(OrderStatus from, OrderStatus to) {
			// Act & Assert
			assertThat(from.canTransitionTo(to)).isTrue();
		}

		static Stream<Arguments> allowedTransitions() {
			return Stream.of(
				// PENDING_PAYMENT → PAID, PAYMENT_FAILED, EXPIRED
				Arguments.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID),
				Arguments.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED),
				Arguments.of(OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED),
				// PAYMENT_FAILED → PENDING_PAYMENT, EXPIRED
				Arguments.of(OrderStatus.PAYMENT_FAILED, OrderStatus.PENDING_PAYMENT),
				Arguments.of(OrderStatus.PAYMENT_FAILED, OrderStatus.EXPIRED)
			);
		}

	}


	@Nested
	@DisplayName("canTransitionTo() — 금지된 전이")
	class ForbiddenTransitionsTest {

		@ParameterizedTest(name = "[{index}] {0} → {1} = false")
		@MethodSource("forbiddenTransitions")
		@DisplayName("[canTransitionTo()] 금지된 상태 전이 -> false")
		void forbiddenTransitionReturnsFalse(OrderStatus from, OrderStatus to) {
			// Act & Assert
			assertThat(from.canTransitionTo(to)).isFalse();
		}

		static Stream<Arguments> forbiddenTransitions() {
			return Stream.of(
				// PENDING_PAYMENT → PENDING_PAYMENT (자기 자신)
				Arguments.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PENDING_PAYMENT),
				// PAYMENT_FAILED → PAID, PAYMENT_FAILED (자기 자신)
				Arguments.of(OrderStatus.PAYMENT_FAILED, OrderStatus.PAID),
				Arguments.of(OrderStatus.PAYMENT_FAILED, OrderStatus.PAYMENT_FAILED),
				// PAID → any (최종 상태)
				Arguments.of(OrderStatus.PAID, OrderStatus.PENDING_PAYMENT),
				Arguments.of(OrderStatus.PAID, OrderStatus.PAID),
				Arguments.of(OrderStatus.PAID, OrderStatus.PAYMENT_FAILED),
				Arguments.of(OrderStatus.PAID, OrderStatus.EXPIRED),
				// EXPIRED → any (최종 상태)
				Arguments.of(OrderStatus.EXPIRED, OrderStatus.PENDING_PAYMENT),
				Arguments.of(OrderStatus.EXPIRED, OrderStatus.PAID),
				Arguments.of(OrderStatus.EXPIRED, OrderStatus.PAYMENT_FAILED),
				Arguments.of(OrderStatus.EXPIRED, OrderStatus.EXPIRED)
			);
		}

	}


	@Test
	@DisplayName("[OrderStatus] enum 상수 개수가 4개임을 보장")
	void enumConstantCount() {
		// Assert
		assertThat(OrderStatus.values()).hasSize(4);
	}

}
