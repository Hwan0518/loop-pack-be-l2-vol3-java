package com.loopers.payment.payment.application.port.out.client.pg.exception;


/**
 * PG 읽기 타임아웃 예외 (Read timeout — 요청 도달, 응답 미수신)
 * - Payment REQUESTED 유지 → 폴링 스케줄러가 후속 처리
 */
public class PgReadTimeoutException extends RuntimeException {

	public PgReadTimeoutException(String message) {
		super(message);
	}

	public PgReadTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}

}
