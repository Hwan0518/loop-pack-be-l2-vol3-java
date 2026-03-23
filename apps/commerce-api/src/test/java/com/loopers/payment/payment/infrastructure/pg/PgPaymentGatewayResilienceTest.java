package com.loopers.payment.payment.infrastructure.pg;


import com.loopers.payment.payment.application.port.out.client.pg.dto.PgPaymentRequest;
import com.loopers.payment.payment.application.port.out.client.pg.dto.PgPaymentResponse;
import com.loopers.payment.payment.application.port.out.client.pg.exception.PgBadRequestException;
import com.loopers.payment.payment.application.port.out.client.pg.exception.PgReadTimeoutException;
import com.loopers.payment.payment.application.port.out.client.pg.exception.PgRequestFailedException;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("PgPaymentGateway Resilience 통합 테스트")
class PgPaymentGatewayResilienceTest {

	@Autowired
	private PgPaymentGatewayImpl pgPaymentGateway;

	@Autowired
	@Qualifier("pgRestTemplate")
	private RestTemplate pgRestTemplate;

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry;

	@Autowired
	private RetryRegistry retryRegistry;

	private MockRestServiceServer mockServer;

	private static final String PG_PAYMENTS_URL = "http://localhost:8082/api/v1/payments";
	private static final String SUCCESS_RESPONSE_BODY = """
		{
			"meta": {"result": "SUCCESS", "errorCode": null, "message": null},
			"data": {"transactionKey": "txn-key", "orderId": "100", "cardType": "SAMSUNG", "cardNo": "1234-5678-9012-3456", "amount": 50000, "status": "REQUESTED", "reason": null}
		}
		""";

	// retry metrics 기준값 (테스트 간 누적 metrics를 delta로 비교)
	private long failedWithRetryBefore;
	private long failedWithoutRetryBefore;
	private long successWithRetryBefore;


	@BeforeEach
	void setUp() {
		mockServer = MockRestServiceServer.createServer(pgRestTemplate);

		// CircuitBreaker 상태 초기화
		CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pgCircuitBreaker");
		cb.reset();
		CircuitBreaker queryCb = circuitBreakerRegistry.circuitBreaker("pgQueryCircuitBreaker");
		queryCb.reset();

		// Retry metrics 기준값 캡처
		Retry retry = retryRegistry.retry("pgRetry");
		failedWithRetryBefore = retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt();
		failedWithoutRetryBefore = retry.getMetrics().getNumberOfFailedCallsWithoutRetryAttempt();
		successWithRetryBefore = retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt();
	}


	@Test
	@DisplayName("[Retry] 3회 연속 실패 -> PgRequestFailedException 전파. Retry metrics: failedWithRetry 1 증가")
	void retryExhausted() {
		// Arrange — 3회 모두 500 응답 (max-attempts=3)
		for (int i = 0; i < 3; i++) {
			mockServer.expect(requestTo(PG_PAYMENTS_URL))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withServerError());
		}
		PgPaymentRequest request = new PgPaymentRequest("100", "SAMSUNG", "1234-5678-9012-3456", 50000L, "http://callback");

		// Act
		assertThrows(PgRequestFailedException.class,
			() -> pgPaymentGateway.requestPayment(1L, request));

		// Assert — 3회 호출 확인 + retry metrics 검증
		Retry retry = retryRegistry.retry("pgRetry");
		long failedWithRetryAfter = retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt();

		assertAll(
			() -> mockServer.verify(),
			() -> assertThat(failedWithRetryAfter - failedWithRetryBefore).isEqualTo(1)
		);
	}


	@Test
	@DisplayName("[Retry] 2회 실패 + 1회 성공 -> 정상 응답 반환. Retry metrics: successWithRetry 1 증가")
	void retrySuccessOnThirdAttempt() {
		// Arrange — 2회 실패 후 1회 성공
		mockServer.expect(requestTo(PG_PAYMENTS_URL))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withServerError());
		mockServer.expect(requestTo(PG_PAYMENTS_URL))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withServerError());
		mockServer.expect(requestTo(PG_PAYMENTS_URL))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess(SUCCESS_RESPONSE_BODY, MediaType.APPLICATION_JSON));
		PgPaymentRequest request = new PgPaymentRequest("100", "SAMSUNG", "1234-5678-9012-3456", 50000L, "http://callback");

		// Act
		PgPaymentResponse result = pgPaymentGateway.requestPayment(1L, request);

		// Assert — 3회 호출 확인 + retry metrics 검증
		Retry retry = retryRegistry.retry("pgRetry");
		long successWithRetryAfter = retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt();

		assertAll(
			() -> assertThat(result.data().transactionKey()).isEqualTo("txn-key"),
			() -> mockServer.verify(),
			() -> assertThat(successWithRetryAfter - successWithRetryBefore).isEqualTo(1)
		);
	}


	@Test
	@DisplayName("[Retry ignore-exceptions] PgReadTimeoutException 발생 -> 재시도 없이 즉시 전파. 호출 1회만 발생")
	void readTimeoutNotRetried() {
		// Arrange — 1회만 expect (재시도 시 2회 이상 호출되면 mockServer.verify() 실패)
		mockServer.expect(requestTo(PG_PAYMENTS_URL))
			.andExpect(method(HttpMethod.POST))
			.andRespond(request -> {
				throw new SocketTimeoutException("Read timed out");
			});
		PgPaymentRequest request = new PgPaymentRequest("100", "SAMSUNG", "1234-5678-9012-3456", 50000L, "http://callback");

		// Act
		assertThrows(PgReadTimeoutException.class,
			() -> pgPaymentGateway.requestPayment(1L, request));

		// Assert — 호출 1회 확인 + retry metrics: failedWithoutRetry 1 증가 (재시도 없이 실패)
		Retry retry = retryRegistry.retry("pgRetry");
		long failedWithoutRetryAfter = retry.getMetrics().getNumberOfFailedCallsWithoutRetryAttempt();

		assertAll(
			() -> mockServer.verify(),
			() -> assertThat(failedWithoutRetryAfter - failedWithoutRetryBefore).isEqualTo(1)
		);
	}


	@Test
	@DisplayName("[CircuitBreaker] OPEN 상태 -> CallNotPermittedException 전파. PG 호출 없이 즉시 차단")
	void circuitBreakerOpen() {
		// Arrange — CircuitBreaker를 강제로 OPEN 상태로 전환
		CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pgCircuitBreaker");
		cb.transitionToOpenState();

		PgPaymentRequest request = new PgPaymentRequest("100", "SAMSUNG", "1234-5678-9012-3456", 50000L, "http://callback");

		// Act & Assert
		assertThrows(CallNotPermittedException.class,
			() -> pgPaymentGateway.requestPayment(1L, request));
	}


	@Test
	@DisplayName("[4xx ignore-exceptions] PG 400 응답 -> PgBadRequestException 전파. CB 실패 기록 안 됨")
	void httpClientErrorNotRecordedInCircuitBreaker() {
		// Arrange — PG가 400 응답
		mockServer.expect(requestTo(PG_PAYMENTS_URL))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withBadRequest());
		PgPaymentRequest request = new PgPaymentRequest("100", "SAMSUNG", "1234-5678-9012-3456", 50000L, "http://callback");

		// Act
		assertThrows(PgBadRequestException.class,
			() -> pgPaymentGateway.requestPayment(1L, request));

		// Assert — PgBadRequestException 전파 + CB 실패 미기록
		CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pgCircuitBreaker");
		assertAll(
			() -> assertThat(cb.getMetrics().getNumberOfFailedCalls()).isZero(),
			() -> mockServer.verify()
		);
	}


	@Test
	@DisplayName("[pgQueryCircuitBreaker] OPEN 상태 -> 조회 메서드 CallNotPermittedException 전파. 결제 요청 CB와 독립 동작")
	void queryCircuitBreakerOpen() {
		// Arrange — pgQueryCircuitBreaker만 OPEN, pgCircuitBreaker는 CLOSED 유지
		CircuitBreaker queryCb = circuitBreakerRegistry.circuitBreaker("pgQueryCircuitBreaker");
		queryCb.transitionToOpenState();

		// Act & Assert — 조회 메서드 차단
		assertAll(
			() -> assertThrows(CallNotPermittedException.class,
				() -> pgPaymentGateway.getPaymentByTransactionKey(1L, "txn-key")),
			() -> assertThrows(CallNotPermittedException.class,
				() -> pgPaymentGateway.getPaymentsByOrderId(1L, "PGO-test"))
		);
	}


	@Test
	@DisplayName("[pgQueryCircuitBreaker 독립성] 결제 CB OPEN + 조회 CB CLOSED -> 조회 정상 호출. 두 CB가 독립적으로 동작")
	void queryCircuitBreakerIndependentFromPayment() {
		// Arrange — pgCircuitBreaker만 OPEN, pgQueryCircuitBreaker는 CLOSED 유지
		CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pgCircuitBreaker");
		cb.transitionToOpenState();

		mockServer.expect(requestTo(PG_PAYMENTS_URL + "/txn-key"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(SUCCESS_RESPONSE_BODY, MediaType.APPLICATION_JSON));

		// Act — 조회는 정상 동작
		PgPaymentResponse result = pgPaymentGateway.getPaymentByTransactionKey(1L, "txn-key");

		// Assert
		assertAll(
			() -> assertThat(result.data().transactionKey()).isEqualTo("txn-key"),
			() -> mockServer.verify()
		);
	}

}
