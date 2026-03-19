package com.loopers.payment.payment.application.port.out.client.pg.exception;


/**
 * PG 요청 오류 예외 (HTTP 4xx — 클라이언트 오류)
 * - 서킷/재시도 대상 아님 → Service에서 PG_BAD_REQUEST CoreException으로 변환
 */
public class PgBadRequestException extends RuntimeException {

	public PgBadRequestException(String message) {
		super(message);
	}

	public PgBadRequestException(String message, Throwable cause) {
		super(message, cause);
	}

}
