package com.loopers.support.idempotency.infrastructure.jpa;


import com.loopers.support.idempotency.infrastructure.entity.EventHandledEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;


/**
 * 이벤트 멱등 처리 JPA 레포지토리
 */
public interface EventHandledJpaRepository extends JpaRepository<EventHandledEntity, Long> {

	// 단건 존재 여부
	boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup);

	// 일괄 존재 여부 (배치 처리용)
	List<EventHandledEntity> findByEventIdInAndConsumerGroup(Collection<String> eventIds, String consumerGroup);

}
