package com.loopers.readmodel.application.service;


import com.loopers.readmodel.application.port.out.ReadModelPort;
import com.loopers.readmodel.application.port.out.ReadModelPort.UpdateResult;
import com.loopers.support.idempotency.EventIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * ReadModel 동기화 서비스
 * 1. snapshot 이벤트 → ProductReadModel 반영 (version 비교)
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class ReadModelSyncService {

	private static final String CONSUMER_GROUP = "read-model-sync";

	// port
	private final ReadModelPort readModelPort;
	// idempotency
	private final EventIdempotencyService eventIdempotencyService;


	// 1. snapshot 반영 (version 비교 + event_handled 기록, 동일 TX)
	@Transactional
	public void syncReadModel(String eventId, Long productId, Long likeCount, Long version) {

		// 멱등 검사
		if (eventIdempotencyService.isAlreadyHandled(eventId, CONSUMER_GROUP)) {
			return;
		}

		// ReadModel 업데이트
		UpdateResult result = readModelPort.updateMetrics(productId, likeCount, version);

		switch (result) {
			case NOT_FOUND -> log.warn("[ReadModelSync] ReadModel 없음 productId={}", productId);
			case SKIPPED_OLD_VERSION -> log.debug("[ReadModelSync] 구버전 skip productId={} incoming={}",
				productId, version);
			case UPDATED -> log.info("[ReadModelSync] productId={} likeCount={} version={}",
				productId, likeCount, version);
		}

		// event_handled 기록
		eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);
	}

}
