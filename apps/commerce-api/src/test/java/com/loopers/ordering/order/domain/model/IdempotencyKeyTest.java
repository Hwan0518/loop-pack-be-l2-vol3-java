package com.loopers.ordering.order.domain.model;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


@DisplayName("IdempotencyKey 도메인 모델 테스트")
class IdempotencyKeyTest {

	@Nested
	@DisplayName("create() 테스트")
	class CreateTest {

		@Test
		@DisplayName("[IdempotencyKey.create()] 유효한 인자 -> IdempotencyKey 생성. id=null, createdAt=null")
		void createSuccess() {
			// Act
			IdempotencyKey key = IdempotencyKey.create(1L, "req-123", 10L);

			// Assert
			assertAll(
				() -> assertThat(key.getId()).isNull(),
				() -> assertThat(key.getUserId()).isEqualTo(1L),
				() -> assertThat(key.getRequestId()).isEqualTo("req-123"),
				() -> assertThat(key.getOrderId()).isEqualTo(10L),
				() -> assertThat(key.getCreatedAt()).isNull()
			);
		}


		@Test
		@DisplayName("[IdempotencyKey.create()] userId가 null -> NullPointerException 예외")
		void createFailWhenUserIdIsNull() {
			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> IdempotencyKey.create(null, "req-123", 10L));
		}


		@Test
		@DisplayName("[IdempotencyKey.create()] requestId가 null -> INVALID_REQUEST_ID 예외")
		void createFailWhenRequestIdIsNull() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> IdempotencyKey.create(1L, null, 10L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_REQUEST_ID),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_REQUEST_ID.getMessage())
			);
		}


		@Test
		@DisplayName("[IdempotencyKey.create()] requestId가 빈 문자열 -> INVALID_REQUEST_ID 예외")
		void createFailWhenRequestIdIsEmpty() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> IdempotencyKey.create(1L, "", 10L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_REQUEST_ID),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_REQUEST_ID.getMessage())
			);
		}


		@Test
		@DisplayName("[IdempotencyKey.create()] requestId가 공백만 -> INVALID_REQUEST_ID 예외")
		void createFailWhenRequestIdIsBlank() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> IdempotencyKey.create(1L, "   ", 10L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_REQUEST_ID),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_REQUEST_ID.getMessage())
			);
		}


		@Test
		@DisplayName("[IdempotencyKey.create()] orderId가 null -> NullPointerException 예외")
		void createFailWhenOrderIdIsNull() {
			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> IdempotencyKey.create(1L, "req-123", null));
		}

	}


	@Nested
	@DisplayName("reconstruct() 테스트")
	class ReconstructTest {

		@Test
		@DisplayName("[IdempotencyKey.reconstruct()] 유효한 인자 -> IdempotencyKey 복원. 검증 없이 값 설정")
		void reconstructSuccess() {
			// Arrange
			LocalDateTime createdAt = LocalDateTime.now();

			// Act
			IdempotencyKey key = IdempotencyKey.reconstruct(1L, 100L, "req-123", 10L, createdAt);

			// Assert
			assertAll(
				() -> assertThat(key.getId()).isEqualTo(1L),
				() -> assertThat(key.getUserId()).isEqualTo(100L),
				() -> assertThat(key.getRequestId()).isEqualTo("req-123"),
				() -> assertThat(key.getOrderId()).isEqualTo(10L),
				() -> assertThat(key.getCreatedAt()).isEqualTo(createdAt)
			);
		}

	}

}
