package com.loopers.support.idempotency.port.out;


import java.util.Collection;
import java.util.Set;


/**
 * 이벤트 멱등 처리 포트
 * 1. 단건 존재 여부 확인
 * 2. 일괄 처리된 eventId 집합 조회
 * 3. 처리 완료 기록
 * 4. 일괄 처리 완료 기록
 */
public interface EventHandledPort {

	// 1. 단건 존재 여부 확인
	boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup);

	// 2. 일괄 처리된 eventId 집합 조회
	Set<String> findHandledEventIds(Collection<String> eventIds, String consumerGroup);

	// 3. 처리 완료 기록
	void markHandled(String eventId, String consumerGroup);

	// 4. 일괄 처리 완료 기록
	void markHandledBatch(Collection<String> eventIds, String consumerGroup);

}
