package com.loopers.readmodel.interfaces.consumer;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.readmodel.application.service.ReadModelSyncService;
import com.loopers.support.common.event.metrics.ProductMetricsSnapshotPayload;
import com.loopers.support.common.outbox.application.dto.KafkaEventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * ReadModel 동기화 Consumer (consumer group: read-model-sync)
 * - product-metrics-snapshots 구독
 * - version 비교로 최신 이벤트만 반영
 */

@Component
@RequiredArgsConstructor
@Slf4j
public class ReadModelSyncConsumer {

	// service
	private final ReadModelSyncService readModelSyncService;
	// json
	private final ObjectMapper objectMapper;


	@KafkaListener(
		topics = "product-metrics-snapshots",
		groupId = "read-model-sync",
		containerFactory = KafkaConfig.BATCH_LISTENER
	)
	public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
		try {
			for (ConsumerRecord<String, byte[]> record : records) {
				KafkaEventEnvelope envelope = objectMapper.readValue(record.value(), KafkaEventEnvelope.class);
				ProductMetricsSnapshotPayload payload = objectMapper.readValue(
					envelope.data(), ProductMetricsSnapshotPayload.class);

				readModelSyncService.syncReadModel(
					envelope.eventId(), payload.productId(), payload.likeCount(), payload.version());
			}
		} catch (Exception e) {
			log.error("[ReadModelSync] 배치 처리 실패", e);
			throw new RuntimeException(e);
		}

		// commit 후 ack
		ack.acknowledge();
	}

}
