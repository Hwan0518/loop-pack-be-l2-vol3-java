package com.loopers.support.common.outbox.infrastructure;


import com.loopers.support.common.outbox.application.dto.OutboxEventDto;
import com.loopers.support.common.outbox.application.port.OutboxEventPort;
import com.loopers.support.common.outbox.infrastructure.entity.OutboxEventApiEntity;
import com.loopers.support.common.outbox.infrastructure.jpa.OutboxEventApiJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;


/**
 * Outbox 이벤트 포트 구현체 (commerce-api 전용)
 * 1. 저장 (PENDING)
 * 2. 재시도 가능 이벤트 조회
 * 3. 발행 완료 처리
 * 4. 실패 처리
 * 5. DEAD 처리
 */

@Repository
@RequiredArgsConstructor
public class OutboxEventPortImpl implements OutboxEventPort {

	private static final int MAX_RETRY = 3;

	// jpa
	private final OutboxEventApiJpaRepository outboxJpaRepository;


	// 1. 저장 (PENDING)
	@Override
	public void save(String aggregateType, String aggregateId, String eventType,
		String topic, String partitionKey, String payload) {
		OutboxEventApiEntity entity = OutboxEventApiEntity.of(
			aggregateType, aggregateId, eventType, topic, partitionKey, payload);
		outboxJpaRepository.save(entity);
	}


	// 2. 재시도 가능 이벤트 조회
	@Override
	public List<OutboxEventDto> findRetryableEvents(int limit) {
		return outboxJpaRepository.findRetryableEvents(MAX_RETRY, limit).stream()
			.map(e -> new OutboxEventDto(
				e.getId(), e.getAggregateType(), e.getAggregateId(),
				e.getEventType(), e.getTopic(), e.getPartitionKey(),
				e.getPayload(), e.getStatus(), e.getRetryCount(), e.getCreatedAt()))
			.toList();
	}


	// 3. 발행 완료 처리
	@Override
	public void markPublished(Long id) {
		outboxJpaRepository.findById(id).ifPresent(OutboxEventApiEntity::markPublished);
	}


	// 4. 실패 처리
	@Override
	public void markFailed(Long id) {
		outboxJpaRepository.findById(id).ifPresent(OutboxEventApiEntity::markFailed);
	}


	// 5. DEAD 처리
	@Override
	public void markDead(Long id) {
		outboxJpaRepository.findById(id).ifPresent(OutboxEventApiEntity::markDead);
	}

}
