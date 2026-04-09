package com.loopers.ranking.interfaces.consumer;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.ranking.application.service.RankingScoreService;
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


@Component
@RequiredArgsConstructor
@Slf4j
public class RankingCollectorConsumer {

	// service
	private final RankingScoreService rankingScoreService;
	// json
	private final ObjectMapper objectMapper;


	/**
	 * 랭킹 수집 Consumer (consumer group: ranking-collector)
	 * - catalog-events, order-events 구독
	 * - 배치 내 같은 productId에 대한 이벤트 타입별 delta 합산 → Redis ZSET 점수 갱신
	 *
	 * 1. 카프카 배치 소비 (eventId 수집 → 멱등 필터 → delta 합산 → 반영)
	 */

	// 1. 카프카 배치 소비
	@KafkaListener(
		topics = {"catalog-events", "order-events"},
		groupId = "ranking-collector",
		containerFactory = KafkaConfig.BATCH_LISTENER
	)
	public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
		try {
			// 1. eventId 수집 + 역직렬화
			Set<String> allEventIds = new LinkedHashSet<>();
			List<KafkaEventEnvelope> envelopes = new ArrayList<>();

			for (ConsumerRecord<String, byte[]> record : records) {
				KafkaEventEnvelope envelope = objectMapper.readValue(record.value(), KafkaEventEnvelope.class);
				allEventIds.add(envelope.eventId());
				envelopes.add(envelope);
			}

			// 이미 처리된 이벤트 필터링
			Set<String> alreadyHandled = rankingScoreService.findAlreadyHandledIds(allEventIds);

			// 2. 배치 내 delta 합산
			Map<Long, long[]> deltas = new HashMap<>(); // productId → [viewDelta, likeDelta, orderDelta]
			Set<String> newEventIds = new LinkedHashSet<>();

			for (KafkaEventEnvelope envelope : envelopes) {
				if (alreadyHandled.contains(envelope.eventId())) continue;

				// 배치 내 중복 eventId 방어 (Set.add가 false면 이미 처리된 이벤트)
				if (!newEventIds.add(envelope.eventId())) continue;

				aggregateDelta(deltas, envelope.eventType(), envelope.data());
			}

			// 3. delta 반영 + event_handled 기록
			if (!deltas.isEmpty()) {
				rankingScoreService.applyDeltas(deltas, newEventIds);
			}
		} catch (Exception e) {
			log.error("[RankingCollector] 배치 처리 실패", e);
			throw new RuntimeException(e);
		}

		// 4. ack
		ack.acknowledge();
	}


	/**
	 * private method
	 * - 이벤트 타입별 delta 합산
	 */

	// 이벤트 타입별 delta 합산
	private void aggregateDelta(Map<Long, long[]> deltas, String eventType, String data) throws Exception {
		switch (eventType) {
			case "PRODUCT_VIEWED" -> {
				var payload = objectMapper.readValue(data,
					com.loopers.support.common.event.catalog.ProductViewedPayload.class);
				deltas.computeIfAbsent(payload.productId(), k -> new long[3])[0] += 1;
			}
			case "PRODUCT_LIKED" -> {
				var payload = objectMapper.readValue(data,
					com.loopers.support.common.event.catalog.ProductLikedPayload.class);
				deltas.computeIfAbsent(payload.productId(), k -> new long[3])[1] += 1;
			}
			case "PRODUCT_UNLIKED" -> {
				var payload = objectMapper.readValue(data,
					com.loopers.support.common.event.catalog.ProductUnlikedPayload.class);
				deltas.computeIfAbsent(payload.productId(), k -> new long[3])[1] -= 1;
			}
			case "ORDER_PAID" -> {
				OrderPaidPayload payload = objectMapper.readValue(data, OrderPaidPayload.class);
				for (OrderItemPayload item : payload.items()) {
					deltas.computeIfAbsent(item.productId(), k -> new long[3])[2] += item.quantity();
				}
			}
			default -> log.debug("[RankingCollector] 무시된 이벤트 타입: {}", eventType);
		}
	}

}
