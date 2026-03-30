package com.loopers.support.idempotency.infrastructure;


import com.loopers.support.idempotency.infrastructure.entity.EventHandledEntity;
import com.loopers.support.idempotency.infrastructure.jpa.EventHandledJpaRepository;
import com.loopers.support.idempotency.port.out.EventHandledPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * 이벤트 멱등 처리 포트 구현체
 */

@Repository
@RequiredArgsConstructor
public class EventHandledPortImpl implements EventHandledPort {

	private final EventHandledJpaRepository jpaRepository;


	@Override
	public boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup) {
		return jpaRepository.existsByEventIdAndConsumerGroup(eventId, consumerGroup);
	}

	@Override
	public Set<String> findHandledEventIds(Collection<String> eventIds, String consumerGroup) {
		return jpaRepository.findByEventIdInAndConsumerGroup(eventIds, consumerGroup).stream()
			.map(EventHandledEntity::getEventId)
			.collect(Collectors.toSet());
	}

	@Override
	public void markHandled(String eventId, String consumerGroup) {
		jpaRepository.save(EventHandledEntity.of(eventId, consumerGroup));
	}

	@Override
	public void markHandledBatch(Collection<String> eventIds, String consumerGroup) {
		List<EventHandledEntity> entities = eventIds.stream()
			.map(id -> EventHandledEntity.of(id, consumerGroup))
			.toList();
		jpaRepository.saveAll(entities);
	}

}
