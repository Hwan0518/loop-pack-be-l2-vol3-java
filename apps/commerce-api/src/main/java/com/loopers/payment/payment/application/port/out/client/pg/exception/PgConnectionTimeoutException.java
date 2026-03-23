package com.loopers.payment.payment.application.port.out.client.pg.exception;


/**
 * PG 연결 타임아웃 예외 (Connection timeout / Connection refused)
 * - Retry 대상 → 재시도 소진 시 Service에서 PG_TIMEOUT으로 변환
 */
public class PgConnectionTimeoutException extends RuntimeException {

	public PgConnectionTimeoutException(String message) {
		super(message);
	}

	public PgConnectionTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}

}
