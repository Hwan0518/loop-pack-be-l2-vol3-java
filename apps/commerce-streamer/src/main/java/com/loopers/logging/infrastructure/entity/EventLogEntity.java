package com.loopers.logging.infrastructure.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


/**
 * 이벤트 로그 엔티티 (event_log — 상세 이력)
 * - 디버깅/운영용 payload, 처리 결과 기록
 */

@Entity
@Table(name = "event_log", indexes = {
	@Index(name = "idx_event_log_event_id", columnList = "event_id"),
	@Index(name = "idx_event_log_type_created", columnList = "event_type, handled_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventLogEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, length = 100)
	private String eventId;

	@Column(name = "consumer_group", nullable = false, length = 100)
	private String consumerGroup;

	@Column(name = "event_type", nullable = false, length = 100)
	private String eventType;

	@Column(name = "aggregate_type", nullable = false, length = 50)
	private String aggregateType;

	@Column(name = "aggregate_id", nullable = false, length = 100)
	private String aggregateId;

	@Column(columnDefinition = "TEXT")
	private String payload;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(name = "error_message", length = 500)
	private String errorMessage;

	@Column(name = "handled_at", nullable = false)
	private LocalDateTime handledAt;


	// 성공 로그
	public static EventLogEntity success(String eventId, String consumerGroup, String eventType,
		String aggregateType, String aggregateId, String payload) {
		EventLogEntity entity = new EventLogEntity();
		entity.eventId = eventId;
		entity.consumerGroup = consumerGroup;
		entity.eventType = eventType;
		entity.aggregateType = aggregateType;
		entity.aggregateId = aggregateId;
		entity.payload = payload;
		entity.status = "SUCCESS";
		entity.handledAt = LocalDateTime.now();
		return entity;
	}


	// 실패 로그
	public static EventLogEntity failure(String eventId, String consumerGroup, String eventType,
		String aggregateType, String aggregateId, String payload, String errorMessage) {
		EventLogEntity entity = new EventLogEntity();
		entity.eventId = eventId;
		entity.consumerGroup = consumerGroup;
		entity.eventType = eventType;
		entity.aggregateType = aggregateType;
		entity.aggregateId = aggregateId;
		entity.payload = payload;
		entity.status = "FAILED";
		entity.errorMessage = errorMessage;
		entity.handledAt = LocalDateTime.now();
		return entity;
	}

}
