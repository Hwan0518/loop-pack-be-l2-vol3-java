package com.loopers.support.idempotency;


import com.loopers.support.idempotency.port.out.EventHandledPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;


/**
 * 이벤트 멱등 처리 서비스
 * 1. 이미 처리된 이벤트인지 확인 (단건)
 * 2. 이미 처리된 eventId 집합 조회 (배치용)
 * 3. 처리 완료 기록
 * 4. 일괄 처리 완료 기록
 */

@Service
@RequiredArgsConstructor
public class EventIdempotencyService {

	// port
	private final EventHandledPort eventHandledPort;


	// 1. 이미 처리된 이벤트인지 확인
	public boolean isAlreadyHandled(String eventId, String consumerGroup) {
		return eventHandledPort.existsByEventIdAndConsumerGroup(eventId, consumerGroup);
	}


	// 2. 이미 처리된 eventId 집합 조회 (배치용)
	public Set<String> findAlreadyHandledIds(Collection<String> eventIds, String consumerGroup) {
		return eventHandledPort.findHandledEventIds(eventIds, consumerGroup);
	}


	// 3. 처리 완료 기록
	public void markHandled(String eventId, String consumerGroup) {
		eventHandledPort.markHandled(eventId, consumerGroup);
	}


	// 4. 일괄 처리 완료 기록
	public void markHandledBatch(Collection<String> eventIds, String consumerGroup) {
		eventHandledPort.markHandledBatch(eventIds, consumerGroup);
	}

}
