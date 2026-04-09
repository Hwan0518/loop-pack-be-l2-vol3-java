package com.loopers.ranking.interfaces.consumer;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.ranking.application.dto.RankingDailyKey;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;


@Component
@RequiredArgsConstructor
@Slf4j
public class RankingCollectorConsumer {

	// 랭킹 집계 날짜 기준 — 단일 규칙: envelope.occurredAt 을 KST LocalDate 로 변환
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	// service
	private final RankingScoreService rankingScoreService;
	// json
	private final ObjectMapper objectMapper;


	/**
	 * 랭킹 수집 Consumer (consumer group: ranking-collector)
	 * - catalog-events, order-events 구독
	 * - DB 단일 TX (counter + score + event_handled) → Redis best-effort → dirty mark
	 *
	 * 1. 카프카 배치 소비
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

			// 2. 배치 내 delta 합산 — (statDate, productId) 단위
			Map<RankingDailyKey, long[]> deltas = new HashMap<>();
			Set<String> newEventIds = new LinkedHashSet<>();

			for (KafkaEventEnvelope envelope : envelopes) {
				if (alreadyHandled.contains(envelope.eventId())) continue;
				if (!newEventIds.add(envelope.eventId())) continue;

				LocalDate statDate = resolveStatDate(envelope);
				aggregateDelta(deltas, statDate, envelope.eventType(), envelope.data());
			}

			// 3. DB 단일 TX — counter + organic_score + event_handled
			if (!deltas.isEmpty()) {
				rankingScoreService.persistDeltas(deltas, newEventIds);

				// 4. Redis — best-effort projection
				// TODO: S8 reconcile job 구현 전까지 Redis 실패 시 해당 날짜 ranking 누락 risk 존재.
				//       reconcile job 이 projection_dirty 를 소비하여 Redis 재생성하면 완전 복구됨.
				try {
					rankingScoreService.reflectToRedis(deltas);
				} catch (Exception e) {
					log.warn("[RankingCollector] Redis 반영 실패 → dirty mark. 해당 날짜 ranking 불완전", e);
					Set<LocalDate> affectedDates = deltas.keySet().stream()
						.map(RankingDailyKey::statDate)
						.collect(Collectors.toSet());
					rankingScoreService.markProjectionDirty(affectedDates);
				}
			}
		} catch (Exception e) {
			log.error("[RankingCollector] 배치 처리 실패", e);
			throw new RuntimeException(e);
		}

		// 5. ack — DB TX 성공이면 항상 ack (Redis 실패는 무시)
		ack.acknowledge();
	}


	/**
	 * private method
	 * - envelope.occurredAt → KST LocalDate 변환
	 * - 이벤트 타입별 delta 합산
	 */

	// envelope.occurredAt → KST LocalDate 변환 (단일 기준)
	private LocalDate resolveStatDate(KafkaEventEnvelope envelope) {
		return envelope.occurredAt().atZone(KST).toLocalDate();
	}


	// 이벤트 타입별 delta 합산
	private void aggregateDelta(Map<RankingDailyKey, long[]> deltas, LocalDate statDate,
		String eventType, String data) throws Exception {
		switch (eventType) {
			case "PRODUCT_VIEWED" -> {
				var payload = objectMapper.readValue(data,
					com.loopers.support.common.event.catalog.ProductViewedPayload.class);
				deltas.computeIfAbsent(new RankingDailyKey(statDate, payload.productId()), k -> new long[3])[0] += 1;
			}
			case "PRODUCT_LIKED" -> {
				var payload = objectMapper.readValue(data,
					com.loopers.support.common.event.catalog.ProductLikedPayload.class);
				deltas.computeIfAbsent(new RankingDailyKey(statDate, payload.productId()), k -> new long[3])[1] += 1;
			}
			case "PRODUCT_UNLIKED" -> {
				var payload = objectMapper.readValue(data,
					com.loopers.support.common.event.catalog.ProductUnlikedPayload.class);
				deltas.computeIfAbsent(new RankingDailyKey(statDate, payload.productId()), k -> new long[3])[1] -= 1;
			}
			case "ORDER_PAID" -> {
				OrderPaidPayload payload = objectMapper.readValue(data, OrderPaidPayload.class);
				for (OrderItemPayload item : payload.items()) {
					deltas.computeIfAbsent(new RankingDailyKey(statDate, item.productId()), k -> new long[3])[2] += item.quantity();
				}
			}
			default -> log.debug("[RankingCollector] 무시된 이벤트 타입: {}", eventType);
		}
	}

}
