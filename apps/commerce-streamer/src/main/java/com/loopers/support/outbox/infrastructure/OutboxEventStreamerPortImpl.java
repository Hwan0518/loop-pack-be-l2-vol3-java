package com.loopers.support.outbox.infrastructure;


import com.loopers.support.common.outbox.application.dto.OutboxEventDto;
import com.loopers.support.common.outbox.application.port.OutboxEventPort;
import com.loopers.support.outbox.infrastructure.entity.OutboxEventStreamerEntity;
import com.loopers.support.outbox.infrastructure.jpa.OutboxEventStreamerJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * Outbox 이벤트 포트 구현체 (commerce-streamer 전용)
 */

@Repository
@RequiredArgsConstructor
public class OutboxEventStreamerPortImpl implements OutboxEventPort {

	private static final int MAX_RETRY = 3;

	private final OutboxEventStreamerJpaRepository outboxJpaRepository;


	@Override
	public void save(String aggregateType, String aggregateId, String eventType,
		String topic, String partitionKey, String payload) {
		outboxJpaRepository.save(OutboxEventStreamerEntity.of(
			aggregateType, aggregateId, eventType, topic, partitionKey, payload));
	}

	@Override
	public List<OutboxEventDto> findRetryableEvents(int limit) {
		return outboxJpaRepository.findRetryableEvents(MAX_RETRY, limit).stream()
			.map(e -> new OutboxEventDto(
				e.getId(), e.getAggregateType(), e.getAggregateId(),
				e.getEventType(), e.getTopic(), e.getPartitionKey(),
				e.getPayload(), e.getStatus(), e.getRetryCount(), e.getCreatedAt()))
			.toList();
	}

	@Transactional
	@Override
	public void markPublished(Long id) {
		outboxJpaRepository.findById(id).ifPresent(entity -> {
			entity.markPublished();
			outboxJpaRepository.save(entity);
		});
	}

	@Transactional
	@Override
	public void markFailed(Long id) {
		outboxJpaRepository.findById(id).ifPresent(entity -> {
			entity.markFailed();
			outboxJpaRepository.save(entity);
		});
	}

	@Transactional
	@Override
	public void markDead(Long id) {
		outboxJpaRepository.findById(id).ifPresent(entity -> {
			entity.markDead();
			outboxJpaRepository.save(entity);
		});
	}

}
