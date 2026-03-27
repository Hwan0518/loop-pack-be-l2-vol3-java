package com.loopers.logging.interfaces.consumer;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.logging.application.dto.EventLogData;
import com.loopers.logging.application.service.UserActionLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.*;


/**
 * 유저 행동 로그 Consumer (consumer group: user-action-logger)
 * - catalog-events, order-events 구독
 * - event_log 중앙화 적재 + 멱등 처리
 */

@Component
@RequiredArgsConstructor
@Slf4j
public class UserActionLogConsumer {

	// service
	private final UserActionLogService userActionLogService;
	// json
	private final ObjectMapper objectMapper;


	@KafkaListener(
		topics = {"catalog-events", "order-events"},
		groupId = "user-action-logger",
		containerFactory = KafkaConfig.BATCH_LISTENER
	)
	public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
		try {
			// 1. eventId 수집 + 멱등 필터링
			Set<String> allEventIds = new LinkedHashSet<>();
			List<JsonNode> envelopes = new ArrayList<>();

			for (ConsumerRecord<String, byte[]> record : records) {
				JsonNode envelope = objectMapper.readTree(record.value());
				allEventIds.add(envelope.get("eventId").asText());
				envelopes.add(envelope);
			}

			Set<String> alreadyHandled = userActionLogService.findAlreadyHandledIds(allEventIds);

			// 2. 신규 이벤트 로그 DTO 생성
			List<EventLogData> logs = new ArrayList<>();
			Set<String> newEventIds = new LinkedHashSet<>();

			for (JsonNode envelope : envelopes) {
				String eventId = envelope.get("eventId").asText();
				if (alreadyHandled.contains(eventId)) continue;

				logs.add(new EventLogData(
					eventId,
					"user-action-logger",
					envelope.get("eventType").asText(),
					envelope.get("aggregateType").asText(),
					envelope.get("aggregateId").asText(),
					envelope.get("data").asText()
				));
				newEventIds.add(eventId);
			}

			// 3. 로그 저장 + event_handled (동일 TX)
			if (!logs.isEmpty()) {
				userActionLogService.saveLogsAndMarkHandled(logs, newEventIds);
			}
		} catch (Exception e) {
			log.error("[UserActionLog] 배치 처리 실패", e);
			throw new RuntimeException(e);
		}

		// 4. ack
		ack.acknowledge();
	}

}
