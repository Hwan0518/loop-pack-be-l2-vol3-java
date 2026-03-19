package com.loopers.payment.payment.application.port.out.client.pg.exception;


/**
 * PG 요청 실패 예외 (HTTP 5xx — 서버 오류)
 * - Retry 대상 → 재시도 소진 시 Service에서 PG_REQUEST_FAILED로 변환
 */
public class PgRequestFailedException extends RuntimeException {

	public PgRequestFailedException(String message) {
		super(message);
	}

	public PgRequestFailedException(String message, Throwable cause) {
		super(message, cause);
	}

}
