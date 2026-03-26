package com.loopers.support.idempotency;


import com.loopers.support.idempotency.infrastructure.entity.EventHandledEntity;
import com.loopers.support.idempotency.infrastructure.jpa.EventHandledJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


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

	// jpa
	private final EventHandledJpaRepository eventHandledJpaRepository;


	// 1. 이미 처리된 이벤트인지 확인
	public boolean isAlreadyHandled(String eventId, String consumerGroup) {
		return eventHandledJpaRepository.existsByEventIdAndConsumerGroup(eventId, consumerGroup);
	}


	// 2. 이미 처리된 eventId 집합 조회 (배치용)
	public Set<String> findAlreadyHandledIds(Collection<String> eventIds, String consumerGroup) {
		return eventHandledJpaRepository.findByEventIdInAndConsumerGroup(eventIds, consumerGroup).stream()
			.map(EventHandledEntity::getEventId)
			.collect(Collectors.toSet());
	}


	// 3. 처리 완료 기록
	public void markHandled(String eventId, String consumerGroup) {
		eventHandledJpaRepository.save(EventHandledEntity.of(eventId, consumerGroup));
	}


	// 4. 일괄 처리 완료 기록
	public void markHandledBatch(Collection<String> eventIds, String consumerGroup) {
		List<EventHandledEntity> entities = eventIds.stream()
			.map(id -> EventHandledEntity.of(id, consumerGroup))
			.toList();
		eventHandledJpaRepository.saveAll(entities);
	}

}
