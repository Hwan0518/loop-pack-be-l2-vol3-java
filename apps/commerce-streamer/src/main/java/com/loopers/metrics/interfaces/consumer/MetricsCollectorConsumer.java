package com.loopers.metrics.interfaces.consumer;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.metrics.application.service.ProductMetricsService;
import com.loopers.support.common.event.EventType;
import com.loopers.support.common.event.ordering.OrderItemPayload;
import com.loopers.support.common.event.ordering.OrderPaidPayload;
import com.loopers.support.common.outbox.application.dto.KafkaEventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.*;


/**
 * 메트릭 수집 Consumer (consumer group: metrics-collector)
 * - catalog-events, order-events 구독
 * - 배치 내 같은 productId에 대한 delta 합산 → product_metrics bulk upsert
 */

@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsCollectorConsumer {

	// service
	private final ProductMetricsService productMetricsService;
	// json
	private final ObjectMapper objectMapper;


	@KafkaListener(
		topics = {"catalog-events", "order-events"},
		groupId = "metrics-collector",
		containerFactory = KafkaConfig.BATCH_LISTENER
	)
	public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
		try {
			// 1. eventId 수집 + 이미 처리된 이벤트 필터링
			Set<String> allEventIds = new LinkedHashSet<>();
			List<KafkaEventEnvelope> envelopes = new ArrayList<>();

			for (ConsumerRecord<String, byte[]> record : records) {
				KafkaEventEnvelope envelope = objectMapper.readValue(record.value(), KafkaEventEnvelope.class);
				allEventIds.add(envelope.eventId());
				envelopes.add(envelope);
			}

			Set<String> alreadyHandled = productMetricsService.findAlreadyHandledIds(allEventIds);

			// 2. 배치 내 delta 합산
			Map<Long, long[]> deltas = new HashMap<>(); // productId → [likeDelta, salesDelta, viewDelta]
			Set<String> newEventIds = new LinkedHashSet<>();

			for (KafkaEventEnvelope envelope : envelopes) {
				if (alreadyHandled.contains(envelope.eventId())) continue;

				aggregateDelta(deltas, envelope.eventType(), envelope.data());
				newEventIds.add(envelope.eventId());
			}

			// 3. delta 반영 + event_handled + snapshot outbox (동일 TX)
			if (!deltas.isEmpty()) {
				productMetricsService.applyDeltasAndPublishSnapshots(deltas, newEventIds);
			}
		} catch (Exception e) {
			log.error("[MetricsCollector] 배치 처리 실패", e);
			throw new RuntimeException(e);
		}

		// 4. ack
		ack.acknowledge();
	}


	// 이벤트 타입별 delta 합산
	private void aggregateDelta(Map<Long, long[]> deltas, String eventType, String data) throws Exception {
		switch (eventType) {
			case "PRODUCT_LIKED" -> {
				var payload = objectMapper.readValue(data, com.loopers.support.common.event.catalog.ProductLikedPayload.class);
				deltas.computeIfAbsent(payload.productId(), k -> new long[3])[0] += 1;
			}
			case "PRODUCT_UNLIKED" -> {
				var payload = objectMapper.readValue(data, com.loopers.support.common.event.catalog.ProductUnlikedPayload.class);
				deltas.computeIfAbsent(payload.productId(), k -> new long[3])[0] -= 1;
			}
			case "PRODUCT_VIEWED" -> {
				var payload = objectMapper.readValue(data, com.loopers.support.common.event.catalog.ProductViewedPayload.class);
				deltas.computeIfAbsent(payload.productId(), k -> new long[3])[2] += 1;
			}
			case "ORDER_PAID" -> {
				OrderPaidPayload payload = objectMapper.readValue(data, OrderPaidPayload.class);
				for (OrderItemPayload item : payload.items()) {
					deltas.computeIfAbsent(item.productId(), k -> new long[3])[1] += item.quantity();
				}
			}
			default -> log.debug("[MetricsCollector] 무시된 이벤트 타입: {}", eventType);
		}
	}

}
