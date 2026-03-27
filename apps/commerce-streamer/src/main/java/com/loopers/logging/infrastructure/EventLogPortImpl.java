package com.loopers.logging.infrastructure;


import com.loopers.logging.application.dto.EventLogData;
import com.loopers.logging.application.port.out.EventLogPort;
import com.loopers.logging.infrastructure.entity.EventLogEntity;
import com.loopers.logging.infrastructure.jpa.EventLogJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;


/**
 * 이벤트 로그 저장 포트 구현체
 * - EventLogJpaRepository 위임
 */

@Repository
@RequiredArgsConstructor
public class EventLogPortImpl implements EventLogPort {

	// jpa
	private final EventLogJpaRepository eventLogJpaRepository;


	// 1. 성공 로그 일괄 저장 (DTO → Entity 변환)
	@Override
	public void saveAll(List<EventLogData> logs) {
		List<EventLogEntity> entities = logs.stream()
			.map(data -> EventLogEntity.success(
				data.eventId(),
				data.consumerGroup(),
				data.eventType(),
				data.aggregateType(),
				data.aggregateId(),
				data.payload()
			))
			.toList();
		eventLogJpaRepository.saveAll(entities);
	}

}
