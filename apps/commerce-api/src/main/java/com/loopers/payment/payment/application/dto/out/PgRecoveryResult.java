package com.loopers.payment.payment.application.dto.out;


/**
 * PG 복구 조회 결과 DTO (infrastructure 의존 없이 결과 전달)
 * - found: PG에서 트랜잭션을 찾았는지 여부
 * - transactionKey: PG 트랜잭션 키 (없으면 null)
 * - status: PG 결제 상태 (SUCCESS/FAILED/PENDING)
 * - reason: 실패 사유 (없으면 null)
 * - byTransactionKey: transactionKey 기반 조회 여부 (false면 orderId 기반)
 */
public record PgRecoveryResult(
	boolean found,
	String transactionKey,
	String status,
	String reason,
	boolean byTransactionKey
) {

	// PG에 트랜잭션 없음
	public static PgRecoveryResult notFound() {
		return new PgRecoveryResult(false, null, null, null, false);
	}

}
