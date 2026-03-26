package com.loopers.metrics.application.service;


import com.loopers.metrics.infrastructure.entity.ProductMetricsEntity;
import com.loopers.metrics.infrastructure.jpa.ProductMetricsJpaRepository;
import com.loopers.support.common.outbox.application.port.OutboxEventPort;
import com.loopers.support.common.outbox.application.util.JsonSerializer;
import com.loopers.support.idempotency.EventIdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;


/**
 * 상품 메트릭 집계 서비스
 * 1. 배치 delta 반영 + event_handled 저장 + snapshot outbox 저장 (동일 TX)
 */

@Service
@RequiredArgsConstructor
public class ProductMetricsService {

	private static final String CONSUMER_GROUP = "metrics-collector";

	// jpa
	private final ProductMetricsJpaRepository productMetricsJpaRepository;
	// idempotency
	private final EventIdempotencyService eventIdempotencyService;
	// outbox (snapshot 발행용)
	private final OutboxEventPort outboxEventPort;
	private final JsonSerializer jsonSerializer;


	/**
	 * 배치 delta 반영 (동일 TX: metrics upsert + event_handled + snapshot outbox)
	 * @param deltas productId → (likeDelta, salesDelta, viewDelta) 맵
	 * @param handledEventIds 이번 배치에서 처리한 eventId 집합
	 */
	@Transactional
	public void applyDeltasAndPublishSnapshots(Map<Long, long[]> deltas, Set<String> handledEventIds) {

		// product_metrics upsert + snapshot outbox 저장
		for (Map.Entry<Long, long[]> entry : deltas.entrySet()) {
			Long productId = entry.getKey();
			long[] delta = entry.getValue(); // [likeDelta, salesDelta, viewDelta]

			// upsert: 존재하면 delta 적용, 없으면 신규 생성
			ProductMetricsEntity metrics = productMetricsJpaRepository.findById(productId)
				.orElseGet(() -> ProductMetricsEntity.createDefault(productId));

			metrics.applyDelta(delta[0], delta[1], delta[2]);
			productMetricsJpaRepository.save(metrics);

			// snapshot outbox 저장 (같은 TX — At Least Once 보장)
			String snapshotPayload = jsonSerializer.toJson(Map.of(
				"productId", metrics.getProductId(),
				"likeCount", metrics.getLikeCount(),
				"salesCount", metrics.getSalesCount(),
				"viewCount", metrics.getViewCount(),
				"version", metrics.getVersion(),
				"updatedAt", metrics.getUpdatedAt().toString()
			));
			outboxEventPort.save("PRODUCT", String.valueOf(productId), "PRODUCT_METRICS_UPDATED",
				"product-metrics-snapshots", String.valueOf(productId), snapshotPayload);
		}

		// event_handled 일괄 저장
		eventIdempotencyService.markHandledBatch(handledEventIds, CONSUMER_GROUP);
	}


	// 이미 처리된 eventId 집합 조회
	public Set<String> findAlreadyHandledIds(Set<String> eventIds) {
		return eventIdempotencyService.findAlreadyHandledIds(eventIds, CONSUMER_GROUP);
	}

}
