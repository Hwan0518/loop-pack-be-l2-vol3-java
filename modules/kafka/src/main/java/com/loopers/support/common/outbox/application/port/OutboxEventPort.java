package com.loopers.support.common.outbox.application.port;


import com.loopers.support.common.outbox.application.dto.OutboxEventDto;

import java.util.List;


/**
 * Outbox 이벤트 포트 (Service TX 내에서 저장, Scheduler에서 조회/상태 변경)
 * 1. 저장
 * 2. 재시도 가능 이벤트 조회
 * 3. 발행 완료 처리
 * 4. 실패 처리
 * 5. DEAD 처리
 */
public interface OutboxEventPort {

	// 1. 저장 (Service TX 내에서 호출)
	void save(String aggregateType, String aggregateId, String eventType,
		String topic, String partitionKey, String payload);

	// 2. 재시도 가능 이벤트 조회 (PENDING + FAILED where retryCount < maxRetry)
	List<OutboxEventDto> findRetryableEvents(int limit);

	// 3. 발행 완료 처리
	void markPublished(Long id);

	// 4. 실패 처리 (retryCount 증가)
	void markFailed(Long id);

	// 5. DEAD 처리 (재시도 소진)
	void markDead(Long id);

}
