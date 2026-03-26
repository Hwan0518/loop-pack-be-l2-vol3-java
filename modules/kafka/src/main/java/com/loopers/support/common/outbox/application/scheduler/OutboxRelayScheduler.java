package com.loopers.support.common.outbox.application.scheduler;


import com.loopers.support.common.outbox.application.dto.KafkaEventEnvelope;
import com.loopers.support.common.outbox.application.dto.OutboxEventDto;
import com.loopers.support.common.outbox.application.port.OutboxEventPort;
import com.loopers.support.common.outbox.application.util.JsonSerializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * Outbox Relay 스케줄러 — PENDING/FAILED 이벤트를 Kafka로 발행
 * - 재시도 정책: MAX_RETRY 3회 초과 시 DEAD 상태 전환
 * - 각 앱에 OutboxEventPortImpl이 하나만 존재하므로 자동 주입
 */

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayScheduler {

	private static final int MAX_RETRY = 3;
	private static final int BATCH_SIZE = 100;

	// port
	private final OutboxEventPort outboxEventPort;
	// kafka
	private final KafkaTemplate<Object, Object> kafkaTemplate;
	// json
	private final JsonSerializer jsonSerializer;


	// Outbox relay — 1초 간격으로 실행
	@Scheduled(fixedDelay = 1000)
	public void relay() {

		// PENDING + FAILED(retryCount < 3) 이벤트 조회
		List<OutboxEventDto> events = outboxEventPort.findRetryableEvents(BATCH_SIZE);

		for (OutboxEventDto event : events) {
			try {
				// Envelope으로 감싸서 발행 (Consumer가 eventId/eventType 등을 파싱)
				KafkaEventEnvelope envelope = new KafkaEventEnvelope(
					String.valueOf(event.id()), event.eventType(),
					event.aggregateType(), event.aggregateId(),
					event.payload(), LocalDateTime.now()
				);

				// Kafka 발행 (동기 대기 — 5초 타임아웃)
				kafkaTemplate.send(event.topic(), event.partitionKey(), jsonSerializer.toJson(envelope))
					.get(5, TimeUnit.SECONDS);

				// 발행 성공 → PUBLISHED
				outboxEventPort.markPublished(event.id());
			} catch (Exception e) {
				// 재시도 정책 판단 (운영 정책)
				if (event.retryCount() + 1 >= MAX_RETRY) {
					outboxEventPort.markDead(event.id());
					log.error("[Outbox] DEAD eventId={}", event.id(), e);
				} else {
					outboxEventPort.markFailed(event.id());
					log.warn("[Outbox] FAILED eventId={} retry={}", event.id(), event.retryCount() + 1, e);
				}
			}
		}
	}

}
