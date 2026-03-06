package com.loopers.support.common.error;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@DisplayName("GlobalExceptionHandler 테스트")
class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Nested
	@DisplayName("CoreException 처리 테스트")
	class HandleCoreExceptionTest {

		@Test
		@DisplayName("[handleCoreException()] CoreException 발생 -> ErrorResponse 반환. "
			+ "status, code, message가 ErrorType에 맞게 설정됨")
		void handleCoreException() {
			// Arrange
			ErrorType errorType = ErrorType.USER_ALREADY_EXISTS;
			CoreException exception = new CoreException(errorType);

			// Act
			ResponseEntity<ErrorResponse> response = handler.handleCoreException(exception);

			// Assert
			assertAll(
				() -> assertThat(response.getStatusCode()).isEqualTo(errorType.getStatus()),
				() -> assertThat(response.getBody()).isNotNull(),
				() -> assertThat(response.getBody().code()).isEqualTo(errorType.getCode()),
				() -> assertThat(response.getBody().message()).isEqualTo(errorType.getMessage())
			);
		}


		@Test
		@DisplayName("[handleCoreException()] BAD_REQUEST ErrorType -> 400 상태코드와 함께 ErrorResponse 반환")
		void handleCoreExceptionWithBadRequest() {
			// Arrange
			ErrorType errorType = ErrorType.INVALID_PASSWORD_FORMAT;
			CoreException exception = new CoreException(errorType);

			// Act
			ResponseEntity<ErrorResponse> response = handler.handleCoreException(exception);

			// Assert
			assertAll(
				() -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
				() -> assertThat(response.getBody()).isNotNull(),
				() -> assertThat(response.getBody().code()).isEqualTo("INVALID_PASSWORD_FORMAT"),
				() -> assertThat(response.getBody().message()).isEqualTo(errorType.getMessage())
			);
		}


		@Test
		@DisplayName("[handleCoreException()] AUTHENTICATION_FAILED -> 401 상태코드와 ErrorResponse 반환. "
			+ "인증 실패 시 AUTHENTICATION_FAILED 코드와 메시지가 정확히 매핑됨")
		void handleCoreExceptionWithUnauthorized() {
			// Arrange
			ErrorType errorType = ErrorType.AUTHENTICATION_FAILED;
			CoreException exception = new CoreException(errorType);

			// Act
			ResponseEntity<ErrorResponse> response = handler.handleCoreException(exception);

			// Assert
			assertAll(
				() -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
				() -> assertThat(response.getBody()).isNotNull(),
				() -> assertThat(response.getBody().code()).isEqualTo("AUTHENTICATION_FAILED"),
				() -> assertThat(response.getBody().message()).isEqualTo(errorType.getMessage())
			);
		}


		@Test
		@DisplayName("[handleCoreException()] INTERNAL_ERROR -> 500 상태코드와 ErrorResponse 반환. "
			+ "서버 오류 시 INTERNAL_ERROR 코드와 메시지가 정확히 매핑됨")
		void handleCoreExceptionWithInternalError() {
			// Arrange
			ErrorType errorType = ErrorType.INTERNAL_ERROR;
			CoreException exception = new CoreException(errorType);

			// Act
			ResponseEntity<ErrorResponse> response = handler.handleCoreException(exception);

			// Assert
			assertAll(
				() -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR),
				() -> assertThat(response.getBody()).isNotNull(),
				() -> assertThat(response.getBody().code()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase()),
				() -> assertThat(response.getBody().message()).isEqualTo(errorType.getMessage())
			);
		}

	}

	@Nested
	@DisplayName("Validation Exception 처리 테스트")
	class HandleValidationExceptionTest {

		@Test
		@DisplayName("[handleValidationException()] MethodArgumentNotValidException 발생 -> BAD_REQUEST 반환. "
			+ "필드명과 에러 메시지가 포함됨")
		void handleValidationException() throws NoSuchMethodException {
			// Arrange
			BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testObject");
			bindingResult.addError(new FieldError("testObject", "loginId", "로그인 ID는 필수입니다."));

			MethodParameter methodParameter = new MethodParameter(
				this.getClass().getDeclaredMethod("handleValidationException"), -1);
			MethodArgumentNotValidException exception = new MethodArgumentNotValidException(methodParameter, bindingResult);

			// Act
			ResponseEntity<ErrorResponse> response = handler.handleValidationException(exception);

			// Assert
			assertAll(
				() -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
				() -> assertThat(response.getBody()).isNotNull(),
				() -> assertThat(response.getBody().code()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
				() -> assertThat(response.getBody().message()).isEqualTo("loginId: 로그인 ID는 필수입니다.")
			);
		}


		@Test
		@DisplayName("[handleValidationException()] 여러 필드 에러 발생 -> 첫 번째 필드 에러만 반환")
		void handleValidationExceptionWithMultipleErrors() throws NoSuchMethodException {
			// Arrange
			BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testObject");
			bindingResult.addError(new FieldError("testObject", "name", "이름은 필수입니다."));
			bindingResult.addError(new FieldError("testObject", "email", "이메일은 필수입니다."));

			MethodParameter methodParameter = new MethodParameter(
				this.getClass().getDeclaredMethod("handleValidationExceptionWithMultipleErrors"), -1);
			MethodArgumentNotValidException exception = new MethodArgumentNotValidException(methodParameter, bindingResult);

			// Act
			ResponseEntity<ErrorResponse> response = handler.handleValidationException(exception);

			// Assert
			assertAll(
				() -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
				() -> assertThat(response.getBody()).isNotNull(),
				() -> assertThat(response.getBody().message()).isEqualTo("name: 이름은 필수입니다.")
			);
		}


		@Test
		@DisplayName("[handleValidationException()] 필드 에러가 없는 경우 -> 기본 메시지 'Validation failed' 반환")
		void handleValidationExceptionWithNoFieldErrors() throws NoSuchMethodException {
			// Arrange
			BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testObject");

			MethodParameter methodParameter = new MethodParameter(
				this.getClass().getDeclaredMethod("handleValidationExceptionWithNoFieldErrors"), -1);
			MethodArgumentNotValidException exception = new MethodArgumentNotValidException(methodParameter, bindingResult);

			// Act
			ResponseEntity<ErrorResponse> response = handler.handleValidationException(exception);

			// Assert
			assertAll(
				() -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
				() -> assertThat(response.getBody()).isNotNull(),
				() -> assertThat(response.getBody().message()).isEqualTo("Validation failed")
			);
		}

	}

	@Nested
	@DisplayName("락 예외 처리 테스트")
	class HandleLockExceptionTest {

		@Test
		@DisplayName("[handlePessimisticLockException()] PessimisticLockingFailureException 발생 -> 409 Conflict 반환. "
			+ "ORDER_STOCK_LOCK_CONFLICT 코드와 메시지가 정확히 매핑됨")
		void handlePessimisticLockingFailureException() {
			// Arrange
			PessimisticLockingFailureException exception =
				new PessimisticLockingFailureException("Lock timeout");

			// Act
			ResponseEntity<ErrorResponse> response = handler.handlePessimisticLockException(exception);

			// Assert
			assertAll(
				() -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
				() -> assertThat(response.getBody()).isNotNull(),
				() -> assertThat(response.getBody().code()).isEqualTo("ORDER_STOCK_LOCK_CONFLICT"),
				() -> assertThat(response.getBody().message()).isEqualTo(ErrorType.ORDER_STOCK_LOCK_CONFLICT.getMessage())
			);
		}


		@Test
		@DisplayName("[handlePessimisticLockException()] CannotAcquireLockException(하위 클래스) 발생 -> 409 Conflict 반환. "
			+ "부모 클래스 핸들러가 하위 클래스도 처리함")
		void handleCannotAcquireLockException() {
			// Arrange
			CannotAcquireLockException exception =
				new CannotAcquireLockException("Cannot acquire lock");

			// Act
			ResponseEntity<ErrorResponse> response = handler.handlePessimisticLockException(exception);

			// Assert
			assertAll(
				() -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
				() -> assertThat(response.getBody()).isNotNull(),
				() -> assertThat(response.getBody().code()).isEqualTo("ORDER_STOCK_LOCK_CONFLICT"),
				() -> assertThat(response.getBody().message()).isEqualTo(ErrorType.ORDER_STOCK_LOCK_CONFLICT.getMessage())
			);
		}


		@Test
		@DisplayName("[handleOptimisticLockException()] OptimisticLockingFailureException 발생 -> 409 Conflict 반환. "
			+ "PRODUCT_CONCURRENT_MODIFICATION 코드와 메시지가 정확히 매핑됨")
		void handleOptimisticLockingFailureException() {
			// Arrange
			OptimisticLockingFailureException exception =
				new OptimisticLockingFailureException("Version conflict");

			// Act
			ResponseEntity<ErrorResponse> response = handler.handleOptimisticLockException(exception);

			// Assert
			assertAll(
				() -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
				() -> assertThat(response.getBody()).isNotNull(),
				() -> assertThat(response.getBody().code()).isEqualTo("PRODUCT_CONCURRENT_MODIFICATION"),
				() -> assertThat(response.getBody().message()).isEqualTo(ErrorType.PRODUCT_CONCURRENT_MODIFICATION.getMessage())
			);
		}


		@Test
		@DisplayName("[handleOptimisticLockException()] ObjectOptimisticLockingFailureException(하위 클래스) 발생 -> 409 Conflict 반환. "
			+ "낙관적 락 핸들러가 하위 클래스도 처리함")
		void handleObjectOptimisticLockingFailureException() {
			// Arrange
			ObjectOptimisticLockingFailureException exception =
				new ObjectOptimisticLockingFailureException("Product", 1L);

			// Act
			ResponseEntity<ErrorResponse> response = handler.handleOptimisticLockException(exception);

			// Assert
			assertAll(
				() -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
				() -> assertThat(response.getBody()).isNotNull(),
				() -> assertThat(response.getBody().code()).isEqualTo("PRODUCT_CONCURRENT_MODIFICATION"),
				() -> assertThat(response.getBody().message()).isEqualTo(ErrorType.PRODUCT_CONCURRENT_MODIFICATION.getMessage())
			);
		}

	}

}
