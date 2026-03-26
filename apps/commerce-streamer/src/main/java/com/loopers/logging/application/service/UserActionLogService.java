package com.loopers.logging.application.service;


import com.loopers.logging.infrastructure.entity.EventLogEntity;
import com.loopers.logging.infrastructure.jpa.EventLogJpaRepository;
import com.loopers.support.idempotency.EventIdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;


/**
 * 유저 행동 로그 서비스
 * 1. 배치 로그 저장 + event_handled 기록 (동일 TX)
 */

@Service
@RequiredArgsConstructor
public class UserActionLogService {

	private static final String CONSUMER_GROUP = "user-action-logger";

	// jpa
	private final EventLogJpaRepository eventLogJpaRepository;
	// idempotency
	private final EventIdempotencyService eventIdempotencyService;


	// 이미 처리된 eventId 집합 조회
	public Set<String> findAlreadyHandledIds(Set<String> eventIds) {
		return eventIdempotencyService.findAlreadyHandledIds(eventIds, CONSUMER_GROUP);
	}


	// 배치 로그 저장 + event_handled (동일 TX)
	@Transactional
	public void saveLogsAndMarkHandled(List<EventLogEntity> logs, Set<String> handledEventIds) {
		eventLogJpaRepository.saveAll(logs);
		eventIdempotencyService.markHandledBatch(handledEventIds, CONSUMER_GROUP);
	}

}
