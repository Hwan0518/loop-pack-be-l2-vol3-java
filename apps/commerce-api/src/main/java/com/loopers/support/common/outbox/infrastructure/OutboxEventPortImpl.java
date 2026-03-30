package com.loopers.support.common.outbox.infrastructure;


import com.loopers.support.common.event.EventType;
import com.loopers.support.common.outbox.application.dto.OutboxEventDto;
import com.loopers.support.common.outbox.application.port.OutboxEventPort;
import com.loopers.support.common.outbox.infrastructure.entity.OutboxEventApiEntity;
import com.loopers.support.common.outbox.infrastructure.jpa.OutboxEventApiJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
	public void save(EventType eventType, String aggregateId, String partitionKey, String payload) {
		OutboxEventApiEntity entity = OutboxEventApiEntity.of(
			eventType.getAggregateType(), aggregateId, eventType.getEventType(),
			eventType.getTopic(), partitionKey, payload);
		outboxJpaRepository.save(entity);
	}


	// 2. 재시도 가능 이벤트 조회
	@Override
	public List<OutboxEventDto> findRetryableEvents(int limit) {
		return outboxJpaRepository.findRetryableEvents(MAX_RETRY, PageRequest.of(0, limit)).stream()
			.map(e -> new OutboxEventDto(
				e.getId(), e.getAggregateType(), e.getAggregateId(),
				e.getEventType(), e.getTopic(), e.getPartitionKey(),
				e.getPayload(), e.getStatus(), e.getRetryCount(), e.getCreatedAt()))
			.toList();
	}


	// 3. 발행 완료 처리
	@Transactional
	@Override
	public void markPublished(Long id) {
		outboxJpaRepository.findById(id).ifPresent(entity -> {
			entity.markPublished();
			outboxJpaRepository.save(entity);
		});
	}


	// 4. 실패 처리
	@Transactional
	@Override
	public void markFailed(Long id) {
		outboxJpaRepository.findById(id).ifPresent(entity -> {
			entity.markFailed();
			outboxJpaRepository.save(entity);
		});
	}


	// 5. DEAD 처리
	@Transactional
	@Override
	public void markDead(Long id) {
		outboxJpaRepository.findById(id).ifPresent(entity -> {
			entity.markDead();
			outboxJpaRepository.save(entity);
		});
	}

}
