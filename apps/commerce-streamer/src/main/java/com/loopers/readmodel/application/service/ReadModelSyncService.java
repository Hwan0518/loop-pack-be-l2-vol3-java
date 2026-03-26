package com.loopers.readmodel.application.service;


import com.loopers.readmodel.infrastructure.entity.StreamerProductReadModelEntity;
import com.loopers.readmodel.infrastructure.jpa.StreamerProductReadModelJpaRepository;
import com.loopers.support.idempotency.EventIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


/**
 * ReadModel 동기화 서비스
 * 1. snapshot 이벤트 → ProductReadModel 반영 (version 비교)
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class ReadModelSyncService {

	private static final String CONSUMER_GROUP = "read-model-sync";

	// jpa
	private final StreamerProductReadModelJpaRepository readModelJpaRepository;
	// idempotency
	private final EventIdempotencyService eventIdempotencyService;


	// 1. snapshot 반영 (version 비교 + event_handled 기록, 동일 TX)
	@Transactional
	public void syncReadModel(String eventId, Long productId, Long likeCount, Long version) {

		// 멱등 검사
		if (eventIdempotencyService.isAlreadyHandled(eventId, CONSUMER_GROUP)) {
			return;
		}

		// ReadModel 조회
		Optional<StreamerProductReadModelEntity> readModelOpt = readModelJpaRepository.findById(productId);
		if (readModelOpt.isEmpty()) {
			log.warn("[ReadModelSync] ReadModel 없음 productId={}", productId);
			eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);
			return;
		}

		StreamerProductReadModelEntity readModel = readModelOpt.get();

		// version 비교: incoming.version <= current.metricsVersion → skip
		if (readModel.getMetricsVersion() != null && version <= readModel.getMetricsVersion()) {
			log.debug("[ReadModelSync] 구버전 skip productId={} incoming={} current={}",
				productId, version, readModel.getMetricsVersion());
			eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);
			return;
		}

		// ReadModel 업데이트
		readModel.updateMetrics(likeCount, version);
		readModelJpaRepository.save(readModel);

		// event_handled 기록
		eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);

		log.info("[ReadModelSync] productId={} likeCount={} version={}", productId, likeCount, version);
	}

}
