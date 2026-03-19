package com.loopers.payment.payment.infrastructure.pg;


import com.loopers.payment.payment.application.port.out.client.pg.dto.PgPaymentListResponse;
import com.loopers.payment.payment.application.port.out.client.pg.dto.PgPaymentRequest;
import com.loopers.payment.payment.application.port.out.client.pg.dto.PgPaymentResponse;
import com.loopers.payment.payment.application.port.out.client.pg.exception.PgBadRequestException;
import com.loopers.payment.payment.application.port.out.client.pg.exception.PgConnectionTimeoutException;
import com.loopers.payment.payment.application.port.out.client.pg.exception.PgReadTimeoutException;
import com.loopers.payment.payment.application.port.out.client.pg.exception.PgRequestFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
@DisplayName("PgPaymentGatewayImpl 단위 테스트")
class PgPaymentGatewayImplTest {

	@Mock
	private RestTemplate pgRestTemplate;

	private PgPaymentGatewayImpl pgPaymentGateway;

	private static final String BASE_URL = "http://localhost:8082";


	@BeforeEach
	void setUp() {
		pgPaymentGateway = new PgPaymentGatewayImpl(pgRestTemplate, BASE_URL);
	}


	@Nested
	@DisplayName("requestPayment 테스트")
	class RequestPaymentTest {

		@Test
		@DisplayName("[requestPayment()] PG 정상 응답 -> PgPaymentResponse 반환")
		void requestPaymentSuccess() {
			// Arrange
			PgPaymentRequest request = new PgPaymentRequest("100", "SAMSUNG", "1234-5678-9012-3456", 50000L, "http://callback");
			PgPaymentResponse expectedResponse = new PgPaymentResponse(
				new PgPaymentResponse.Meta("SUCCESS", null, null),
				new PgPaymentResponse.Data("txn-key-123", "100", "SAMSUNG", "1234-5678-9012-3456", 50000L, "REQUESTED", null)
			);

			given(pgRestTemplate.exchange(
				eq(BASE_URL + "/api/v1/payments"),
				eq(HttpMethod.POST),
				any(HttpEntity.class),
				eq(PgPaymentResponse.class)
			)).willReturn(ResponseEntity.ok(expectedResponse));

			// Act
			PgPaymentResponse result = pgPaymentGateway.requestPayment(1L, request);

			// Assert
			assertAll(
				() -> assertThat(result.meta().result()).isEqualTo("SUCCESS"),
				() -> assertThat(result.data().transactionKey()).isEqualTo("txn-key-123"),
				() -> assertThat(result.data().orderId()).isEqualTo("100"),
				() -> assertThat(result.data().amount()).isEqualTo(50000L)
			);
		}

		@Test
		@DisplayName("[requestPayment()] PG HTTP 500 -> PgRequestFailedException 발생")
		void requestPaymentServerError() {
			// Arrange
			PgPaymentRequest request = new PgPaymentRequest("100", "SAMSUNG", "1234-5678-9012-3456", 50000L, "http://callback");

			given(pgRestTemplate.exchange(
				eq(BASE_URL + "/api/v1/payments"),
				eq(HttpMethod.POST),
				any(HttpEntity.class),
				eq(PgPaymentResponse.class)
			)).willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

			// Act & Assert
			assertThrows(PgRequestFailedException.class,
				() -> pgPaymentGateway.requestPayment(1L, request));
		}

		@Test
		@DisplayName("[requestPayment()] Connection timeout -> PgConnectionTimeoutException 발생")
		void requestPaymentConnectionTimeout() {
			// Arrange
			PgPaymentRequest request = new PgPaymentRequest("100", "SAMSUNG", "1234-5678-9012-3456", 50000L, "http://callback");

			given(pgRestTemplate.exchange(
				eq(BASE_URL + "/api/v1/payments"),
				eq(HttpMethod.POST),
				any(HttpEntity.class),
				eq(PgPaymentResponse.class)
			)).willThrow(new ResourceAccessException("Connection refused", new ConnectException("Connection refused")));

			// Act & Assert
			assertThrows(PgConnectionTimeoutException.class,
				() -> pgPaymentGateway.requestPayment(1L, request));
		}

		@Test
		@DisplayName("[requestPayment()] PG HTTP 400 -> PgBadRequestException 발생. 4xx는 PG 장애가 아닌 요청 오류")
		void requestPaymentClientError() {
			// Arrange
			PgPaymentRequest request = new PgPaymentRequest("100", "SAMSUNG", "1234-5678-9012-3456", 50000L, "http://callback");

			given(pgRestTemplate.exchange(
				eq(BASE_URL + "/api/v1/payments"),
				eq(HttpMethod.POST),
				any(HttpEntity.class),
				eq(PgPaymentResponse.class)
			)).willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

			// Act & Assert
			assertThrows(PgBadRequestException.class,
				() -> pgPaymentGateway.requestPayment(1L, request));
		}

		@Test
		@DisplayName("[requestPayment()] Read timeout -> PgReadTimeoutException 발생")
		void requestPaymentReadTimeout() {
			// Arrange
			PgPaymentRequest request = new PgPaymentRequest("100", "SAMSUNG", "1234-5678-9012-3456", 50000L, "http://callback");

			given(pgRestTemplate.exchange(
				eq(BASE_URL + "/api/v1/payments"),
				eq(HttpMethod.POST),
				any(HttpEntity.class),
				eq(PgPaymentResponse.class)
			)).willThrow(new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out")));

			// Act & Assert
			assertThrows(PgReadTimeoutException.class,
				() -> pgPaymentGateway.requestPayment(1L, request));
		}
	}


	@Nested
	@DisplayName("getPaymentByTransactionKey 테스트")
	class GetPaymentByTransactionKeyTest {

		@Test
		@DisplayName("[getPaymentByTransactionKey()] PG 정상 응답 -> PgPaymentResponse 반환")
		void getPaymentByTransactionKeySuccess() {
			// Arrange
			PgPaymentResponse expectedResponse = new PgPaymentResponse(
				new PgPaymentResponse.Meta("SUCCESS", null, null),
				new PgPaymentResponse.Data("txn-key-123", "100", "SAMSUNG", "1234-5678-9012-3456", 50000L, "SUCCESS", null)
			);

			given(pgRestTemplate.exchange(
				eq(BASE_URL + "/api/v1/payments/txn-key-123"),
				eq(HttpMethod.GET),
				any(HttpEntity.class),
				eq(PgPaymentResponse.class)
			)).willReturn(ResponseEntity.ok(expectedResponse));

			// Act
			PgPaymentResponse result = pgPaymentGateway.getPaymentByTransactionKey(1L, "txn-key-123");

			// Assert
			assertAll(
				() -> assertThat(result.data().transactionKey()).isEqualTo("txn-key-123"),
				() -> assertThat(result.data().status()).isEqualTo("SUCCESS")
			);
		}

		@Test
		@DisplayName("[getPaymentByTransactionKey()] PG HTTP 400 -> PgBadRequestException 발생")
		void getPaymentByTransactionKeyClientError() {
			// Arrange
			given(pgRestTemplate.exchange(
				eq(BASE_URL + "/api/v1/payments/txn-key-123"),
				eq(HttpMethod.GET),
				any(HttpEntity.class),
				eq(PgPaymentResponse.class)
			)).willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

			// Act & Assert
			assertThrows(PgBadRequestException.class,
				() -> pgPaymentGateway.getPaymentByTransactionKey(1L, "txn-key-123"));
		}
	}


	@Nested
	@DisplayName("getPaymentsByOrderId 테스트")
	class GetPaymentsByOrderIdTest {

		@Test
		@DisplayName("[getPaymentsByOrderId()] PG 정상 응답 -> PgPaymentListResponse 반환")
		void getPaymentsByOrderIdSuccess() {
			// Arrange
			PgPaymentListResponse expectedResponse = new PgPaymentListResponse(
				new PgPaymentListResponse.Meta("SUCCESS", null, null),
				new PgPaymentListResponse.Data("100", List.of(
					new PgPaymentListResponse.Transaction("txn-key-1", "SAMSUNG", "1234-5678-9012-3456", 50000L, "SUCCESS", null)
				))
			);

			given(pgRestTemplate.exchange(
				eq(BASE_URL + "/api/v1/payments?orderId=PGO-test"),
				eq(HttpMethod.GET),
				any(HttpEntity.class),
				eq(PgPaymentListResponse.class)
			)).willReturn(ResponseEntity.ok(expectedResponse));

			// Act
			PgPaymentListResponse result = pgPaymentGateway.getPaymentsByOrderId(1L, "PGO-test");

			// Assert
			assertAll(
				() -> assertThat(result.data().orderId()).isEqualTo("100"),
				() -> assertThat(result.data().transactions()).hasSize(1),
				() -> assertThat(result.data().transactions().get(0).transactionKey()).isEqualTo("txn-key-1")
			);
		}

		@Test
		@DisplayName("[getPaymentsByOrderId()] PG HTTP 400 -> PgBadRequestException 발생")
		void getPaymentsByOrderIdClientError() {
			// Arrange
			given(pgRestTemplate.exchange(
				eq(BASE_URL + "/api/v1/payments?orderId=PGO-test"),
				eq(HttpMethod.GET),
				any(HttpEntity.class),
				eq(PgPaymentListResponse.class)
			)).willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

			// Act & Assert
			assertThrows(PgBadRequestException.class,
				() -> pgPaymentGateway.getPaymentsByOrderId(1L, "PGO-test"));
		}
	}

}
