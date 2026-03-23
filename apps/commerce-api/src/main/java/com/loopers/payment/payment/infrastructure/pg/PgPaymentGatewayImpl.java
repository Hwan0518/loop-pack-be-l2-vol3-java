package com.loopers.payment.payment.infrastructure.pg;


import com.loopers.payment.payment.application.port.out.client.pg.PgPaymentGateway;
import com.loopers.payment.payment.application.port.out.client.pg.dto.PgPaymentListResponse;
import com.loopers.payment.payment.application.port.out.client.pg.dto.PgPaymentRequest;
import com.loopers.payment.payment.application.port.out.client.pg.dto.PgPaymentResponse;
import com.loopers.payment.payment.application.port.out.client.pg.exception.PgBadRequestException;
import com.loopers.payment.payment.application.port.out.client.pg.exception.PgConnectionTimeoutException;
import com.loopers.payment.payment.application.port.out.client.pg.exception.PgReadTimeoutException;
import com.loopers.payment.payment.application.port.out.client.pg.exception.PgRequestFailedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;


@Component
public class PgPaymentGatewayImpl implements PgPaymentGateway {

	// config
	private final RestTemplate pgRestTemplate;
	private final String baseUrl;

	public PgPaymentGatewayImpl(
		@Qualifier("pgRestTemplate") RestTemplate pgRestTemplate,
		@Value("${payment.pg.base-url}") String baseUrl
	) {
		this.pgRestTemplate = pgRestTemplate;
		this.baseUrl = baseUrl;
	}


	/**
	 * PG 결제 게이트웨이 구현체
	 * 1. 결제 요청 (Retry + CircuitBreaker)
	 * 2. 트랜잭션 키로 결제 조회 (CircuitBreaker)
	 * 3. 주문 ID로 결제 목록 조회 (CircuitBreaker)
	 */

	// 1. 결제 요청 (Retry + CircuitBreaker 적용)
	@Override
	@Retry(name = "pgRetry")
	@CircuitBreaker(name = "pgCircuitBreaker")
	public PgPaymentResponse requestPayment(Long userId, PgPaymentRequest request) {
		try {
			// 요청 헤더 + 본문 구성
			HttpHeaders headers = createHeaders(userId);
			HttpEntity<PgPaymentRequest> httpEntity = new HttpEntity<>(request, headers);

			// PG 결제 요청
			ResponseEntity<PgPaymentResponse> response = pgRestTemplate.exchange(
				baseUrl + "/api/v1/payments",
				HttpMethod.POST,
				httpEntity,
				PgPaymentResponse.class
			);

			return requireBody(response);
		} catch (HttpClientErrorException e) {
			// 4xx: 요청 오류 → PG 장애가 아님, 서킷/재시도 대상 제외
			throw new PgBadRequestException("PG 요청 오류: " + e.getStatusCode(), e);
		} catch (HttpServerErrorException e) {
			throw new PgRequestFailedException("PG 서버 오류: " + e.getStatusCode(), e);
		} catch (ResourceAccessException e) {
			throw classifyResourceAccessException(e);
		}
	}


	// 2. 트랜잭션 키로 결제 조회
	@Override
	@CircuitBreaker(name = "pgQueryCircuitBreaker")
	public PgPaymentResponse getPaymentByTransactionKey(Long userId, String transactionKey) {
		try {
			// 요청 헤더 구성
			HttpHeaders headers = createHeaders(userId);
			HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

			// PG 결제 조회
			ResponseEntity<PgPaymentResponse> response = pgRestTemplate.exchange(
				baseUrl + "/api/v1/payments/" + transactionKey,
				HttpMethod.GET,
				httpEntity,
				PgPaymentResponse.class
			);

			return requireBody(response);
		} catch (HttpClientErrorException e) {
			// 4xx: 요청 오류 → PG 장애가 아님, 서킷/재시도 대상 제외
			throw new PgBadRequestException("PG 요청 오류: " + e.getStatusCode(), e);
		} catch (HttpServerErrorException e) {
			throw new PgRequestFailedException("PG 서버 오류: " + e.getStatusCode(), e);
		} catch (ResourceAccessException e) {
			throw classifyResourceAccessException(e);
		}
	}


	// 3. 주문 ID로 결제 목록 조회
	@Override
	@CircuitBreaker(name = "pgQueryCircuitBreaker")
	public PgPaymentListResponse getPaymentsByOrderId(Long userId, String pgOrderId) {
		try {
			// 요청 헤더 구성
			HttpHeaders headers = createHeaders(userId);
			HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

			// PG 주문별 결제 목록 조회
			ResponseEntity<PgPaymentListResponse> response = pgRestTemplate.exchange(
				baseUrl + "/api/v1/payments?orderId=" + pgOrderId,
				HttpMethod.GET,
				httpEntity,
				PgPaymentListResponse.class
			);

			return requireBody(response);
		} catch (HttpClientErrorException e) {
			// 4xx: 요청 오류 → PG 장애가 아님, 서킷/재시도 대상 제외
			throw new PgBadRequestException("PG 요청 오류: " + e.getStatusCode(), e);
		} catch (HttpServerErrorException e) {
			throw new PgRequestFailedException("PG 서버 오류: " + e.getStatusCode(), e);
		} catch (ResourceAccessException e) {
			throw classifyResourceAccessException(e);
		}
	}


	/**
	 * private method
	 * - 요청 헤더 생성 (사용자 ID)
	 * - ResourceAccessException 원인 분류 (connect/read timeout 구분)
	 * - 응답 본문 null 검증
	 */

	// 요청 헤더 생성 (사용자 ID)
	private HttpHeaders createHeaders(Long userId) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-USER-ID", String.valueOf(userId));
		return headers;
	}

	// ResourceAccessException 원인 분류 (connect/read timeout 구분)
	private RuntimeException classifyResourceAccessException(ResourceAccessException e) {
		Throwable cause = e.getCause();

		// SocketTimeoutException: 메시지로 connect/read 구분
		if (cause instanceof SocketTimeoutException ste) {
			String message = ste.getMessage();
			if (message != null && message.toLowerCase().contains("connect")) {
				return new PgConnectionTimeoutException("PG 연결 타임아웃", e);
			}
			return new PgReadTimeoutException("PG 읽기 타임아웃", e);
		}

		// ConnectException: 연결 거부
		if (cause instanceof ConnectException) {
			return new PgConnectionTimeoutException("PG 연결 타임아웃", e);
		}

		return new PgConnectionTimeoutException("PG 연결 실패", e);
	}

	// 응답 본문 null 검증
	private <T> T requireBody(ResponseEntity<T> response) {
		T body = response.getBody();
		if (body == null) {
			throw new PgRequestFailedException("PG 응답 본문이 비어있습니다.");
		}
		return body;
	}

}
