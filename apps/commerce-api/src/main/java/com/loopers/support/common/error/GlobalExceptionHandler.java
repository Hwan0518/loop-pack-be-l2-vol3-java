package com.loopers.support.common.error;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(CoreException.class)
	public ResponseEntity<ErrorResponse> handleCoreException(CoreException e) {
		ErrorType errorType = e.getErrorType();
		ErrorResponse response = ErrorResponse.from(errorType);
		return ResponseEntity.status(errorType.getStatus()).body(response);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
			.findFirst()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.orElse("Validation failed");

		ErrorResponse response = ErrorResponse.of(ErrorType.BAD_REQUEST.getCode(), message);
		return ResponseEntity.status(ErrorType.BAD_REQUEST.getStatus()).body(response);
	}

	// 낙관적 락 충돌 안전망 → 409 Conflict (예상 지점에서 잡히지 않은 경우 범용 메시지)
	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<ErrorResponse> handleOptimisticLockException(OptimisticLockingFailureException e) {
		ErrorType errorType = ErrorType.OPTIMISTIC_LOCK_CONFLICT;
		ErrorResponse response = ErrorResponse.from(errorType);
		return ResponseEntity.status(errorType.getStatus()).body(response);
	}

	// 비관적 락 충돌 안전망 (락 타임아웃/획득 실패 포함) → 409 Conflict
	@ExceptionHandler(PessimisticLockingFailureException.class)
	public ResponseEntity<ErrorResponse> handlePessimisticLockException(PessimisticLockingFailureException e) {
		ErrorType errorType = ErrorType.PESSIMISTIC_LOCK_CONFLICT;
		ErrorResponse response = ErrorResponse.from(errorType);
		return ResponseEntity.status(errorType.getStatus()).body(response);
	}
}
