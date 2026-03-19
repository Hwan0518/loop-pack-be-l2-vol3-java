package com.loopers.payment.payment.infrastructure.pg;


import com.loopers.payment.payment.application.port.out.client.pg.exception.PgRequestFailedException;
import com.loopers.payment.payment.interfaces.TestPgSimulatorController;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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
		"server.port=18080",
		"payment.pg.base-url=http://localhost:18080/test-pg",
		"resilience4j.circuitbreaker.instances.pgQueryCircuitBreaker.sliding-window-type=TIME_BASED",
		"resilience4j.circuitbreaker.instances.pgQueryCircuitBreaker.sliding-window-size=2",
		"resilience4j.circuitbreaker.instances.pgQueryCircuitBreaker.minimum-number-of-calls=10",
		"resilience4j.circuitbreaker.instances.pgQueryCircuitBreaker.failure-rate-threshold=50",
		"resilience4j.circuitbreaker.instances.pgQueryCircuitBreaker.wait-duration-in-open-state=1s",
		"resilience4j.circuitbreaker.instances.pgQueryCircuitBreaker.permitted-number-of-calls-in-half-open-state=2",
		"resilience4j.circuitbreaker.instances.pgQueryCircuitBreaker.automatic-transition-from-open-to-half-open-enabled=true"
	}
)
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("pgQueryCircuitBreaker TIME_BASED 실제 동작 검증")
class PgQueryCircuitBreakerTimeBasedSemanticsTest {

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
		transactionKey = TestPgSimulatorController.seedTransaction("TIME-ORDER-001");
	}


	@AfterEach
	void tearDown() {
		TestPgSimulatorController.reset();
		circuitBreakerRegistry.circuitBreaker("pgCircuitBreaker").reset();
		queryCircuitBreaker.reset();
	}


	@Test
	@DisplayName("minimumNumberOfCalls에 도달하기 전까지는 OPEN 되지 않고, 10번째 실패에서 OPEN 된다")
	void minimumCallsDefersOpenUntilTenthFailure() {
		// Arrange — 연속 실패로 minimumNumberOfCalls 도달 + OPEN 전환 검증
		TestPgSimulatorController.setStickyQueryBehavior(TestPgSimulatorController.PgBehavior.FAIL_500);

		// Act — 9회 실패 (minimumNumberOfCalls 미달)
		for (int i = 0; i < 9; i++) {
			assertThrows(PgRequestFailedException.class,
				() -> pgPaymentGateway.getPaymentByTransactionKey(1L, transactionKey));
		}

		// Assert — 아직 CLOSED (minimumNumberOfCalls=10 미달)
		assertThat(queryCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
		assertThat(queryCircuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(9);
		assertThat(queryCircuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(9);

		// Act — 10번째 실패 → OPEN
		assertThrows(PgRequestFailedException.class,
			() -> pgPaymentGateway.getPaymentByTransactionKey(1L, transactionKey));

		// Assert — OPEN 전환
		assertThat(queryCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
		assertThat(queryCircuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(10);
		assertThat(queryCircuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(10);

		// Act + Assert — OPEN 상태에서 CallNotPermittedException
		assertThrows(CallNotPermittedException.class,
			() -> pgPaymentGateway.getPaymentByTransactionKey(1L, transactionKey));
	}


	@Test
	@DisplayName("TIME_BASED는 시간이 지난 뒤 다음 호출이 들어오면 이전 실패가 윈도우에서 사라진다")
	void timeBasedWindowExpiresFailuresAfterElapsedTime() throws Exception {
		// Arrange
		TestPgSimulatorController.setStickyQueryBehavior(TestPgSimulatorController.PgBehavior.FAIL_500);

		// Act — 3회 실패
		for (int i = 0; i < 3; i++) {
			assertThrows(PgRequestFailedException.class,
				() -> pgPaymentGateway.getPaymentByTransactionKey(1L, transactionKey));
		}

		// Assert — 3회 실패 기록됨
		assertThat(queryCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
		assertThat(queryCircuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(3);
		assertThat(queryCircuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(3);

		// Act — 윈도우 시간(2초) 경과 후 성공 호출
		Thread.sleep(2500);

		TestPgSimulatorController.setStickyQueryBehavior(TestPgSimulatorController.PgBehavior.SUCCESS);
		assertDoesNotThrow(() -> pgPaymentGateway.getPaymentByTransactionKey(1L, transactionKey));

		// Assert — TIME_BASED이므로 이전 실패가 윈도우에서 사라짐
		assertThat(queryCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
		assertThat(queryCircuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
		assertThat(queryCircuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
	}


	@Test
	@DisplayName("HALF_OPEN에서 복구에 성공해 CLOSED가 되면 이전 윈도우는 초기화된다")
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
