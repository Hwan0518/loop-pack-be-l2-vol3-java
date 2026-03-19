package com.loopers.payment.payment.infrastructure.pg;


import com.loopers.payment.payment.application.port.out.client.pg.exception.PgRequestFailedException;
import com.loopers.payment.payment.interfaces.TestPgSimulatorController;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;


@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
	properties = {
		"server.port=18081",
		"payment.pg.base-url=http://localhost:18081/test-pg",
		"resilience4j.circuitbreaker.instances.pgQueryCircuitBreaker.sliding-window-type=COUNT_BASED",
		"resilience4j.circuitbreaker.instances.pgQueryCircuitBreaker.sliding-window-size=10",
		"resilience4j.circuitbreaker.instances.pgQueryCircuitBreaker.minimum-number-of-calls=10",
		"resilience4j.circuitbreaker.instances.pgQueryCircuitBreaker.failure-rate-threshold=50",
		"resilience4j.circuitbreaker.instances.pgQueryCircuitBreaker.wait-duration-in-open-state=1s",
		"resilience4j.circuitbreaker.instances.pgQueryCircuitBreaker.permitted-number-of-calls-in-half-open-state=2",
		"resilience4j.circuitbreaker.instances.pgQueryCircuitBreaker.automatic-transition-from-open-to-half-open-enabled=true"
	}
)
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("pgQueryCircuitBreaker COUNT_BASED 실제 동작 검증")
class PgQueryCircuitBreakerCountBasedSemanticsTest {

	@Autowired
	private PgPaymentGatewayImpl pgPaymentGateway;

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry;

	private CircuitBreaker queryCircuitBreaker;
	private String transactionKey;


	@BeforeEach
	void setUp() {
		TestPgSimulatorController.reset();
		circuitBreakerRegistry.circuitBreaker("pgCircuitBreaker").reset();
		queryCircuitBreaker = circuitBreakerRegistry.circuitBreaker("pgQueryCircuitBreaker");
		queryCircuitBreaker.reset();
		transactionKey = TestPgSimulatorController.seedTransaction("COUNT-ORDER-001");
	}


	@AfterEach
	void tearDown() {
		TestPgSimulatorController.reset();
		circuitBreakerRegistry.circuitBreaker("pgCircuitBreaker").reset();
		queryCircuitBreaker.reset();
	}


	@Test
	@DisplayName("COUNT_BASED는 시간이 지나도 실패를 유지하고, 새 호출이 들어와야 오래된 실패가 밀려난다")
	void countBasedWindowKeepsFailuresUntilMoreCallsArrive() throws Exception {
		// Arrange — 3회 실패 유도 후 성공으로 전환하여 윈도우 밀림 검증
		TestPgSimulatorController.setStickyQueryBehavior(TestPgSimulatorController.PgBehavior.FAIL_500);

		// Act — 3회 실패
		for (int i = 0; i < 3; i++) {
			assertThrows(PgRequestFailedException.class,
				() -> pgPaymentGateway.getPaymentByTransactionKey(1L, transactionKey));
		}

		// Assert — 3회 실패 후 여전히 CLOSED (minimumNumberOfCalls 미달)
		assertThat(queryCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
		assertThat(queryCircuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(3);
		assertThat(queryCircuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(3);

		// Act — 시간 경과 후 성공 호출
		Thread.sleep(2500);

		TestPgSimulatorController.setStickyQueryBehavior(TestPgSimulatorController.PgBehavior.SUCCESS);
		assertDoesNotThrow(() -> pgPaymentGateway.getPaymentByTransactionKey(1L, transactionKey));

		// Assert — COUNT_BASED이므로 이전 실패가 여전히 윈도우에 남아있음
		assertThat(queryCircuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(4);
		assertThat(queryCircuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(3);

		// Act — 성공 6회 추가 → 윈도우 10개 꽉 참
		for (int i = 0; i < 6; i++) {
			assertDoesNotThrow(() -> pgPaymentGateway.getPaymentByTransactionKey(1L, transactionKey));
		}

		// Assert — 윈도우 10개, 실패 3개 (30% < 50% 임계값 → CLOSED 유지)
		assertThat(queryCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
		assertThat(queryCircuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(10);
		assertThat(queryCircuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(3);

		// Act — 1회 추가 성공 → 가장 오래된 실패 1건 밀려남
		assertDoesNotThrow(() -> pgPaymentGateway.getPaymentByTransactionKey(1L, transactionKey));

		// Assert — 실패 2개로 감소 (오래된 실패가 밀려남)
		assertThat(queryCircuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(10);
		assertThat(queryCircuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
	}


	@Test
	@DisplayName("COUNT_BASED도 HALF_OPEN에서 복구에 성공해 CLOSED가 되면 이전 윈도우는 초기화된다")
	void halfOpenSuccessResetsWindow() throws Exception {
		// Arrange
		TestPgSimulatorController.setStickyQueryBehavior(TestPgSimulatorController.PgBehavior.FAIL_500);

		// Act — 10회 실패 → OPEN
		for (int i = 0; i < 10; i++) {
			assertThrows(PgRequestFailedException.class,
				() -> pgPaymentGateway.getPaymentByTransactionKey(1L, transactionKey));
		}

		// Assert — OPEN 상태
		assertThat(queryCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

		// Act — waitDuration 경과 후 HALF_OPEN → 성공 2회
		Thread.sleep(1500);

		TestPgSimulatorController.setStickyQueryBehavior(TestPgSimulatorController.PgBehavior.SUCCESS);
		assertDoesNotThrow(() -> pgPaymentGateway.getPaymentByTransactionKey(1L, transactionKey));
		assertDoesNotThrow(() -> pgPaymentGateway.getPaymentByTransactionKey(1L, transactionKey));

		// Assert — CLOSED 복귀 + 윈도우 초기화
		assertThat(queryCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
		assertThat(queryCircuitBreaker.getMetrics().getNumberOfBufferedCalls()).isZero();
		assertThat(queryCircuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
	}

}
