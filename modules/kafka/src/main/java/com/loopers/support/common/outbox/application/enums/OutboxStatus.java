package com.loopers.support.common.outbox.application.enums;


/**
 * Outbox 이벤트 상태
 * - PENDING: 발행 대기
 * - PUBLISHED: 발행 완료
 * - FAILED: 발행 실패 (재시도 대상)
 * - DEAD: 재시도 소진 (운영자 수동 처리 필요)
 */
public enum OutboxStatus {

	PENDING,
	PUBLISHED,
	FAILED,
	DEAD

}
