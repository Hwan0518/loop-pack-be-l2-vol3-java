package com.loopers.support.idempotency.infrastructure.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


/**
 * 이벤트 멱등 처리 엔티티 (event_handled)
 * - 멱등 판정에만 사용 (최소 데이터, 빠른 조회)
 */

@Entity
@Table(name = "event_handled", indexes = {
	@Index(name = "idx_event_handled_event_group", columnList = "event_id, consumer_group", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventHandledEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, length = 100)
	private String eventId;

	@Column(name = "consumer_group", nullable = false, length = 100)
	private String consumerGroup;

	@Column(name = "handled_at", nullable = false)
	private LocalDateTime handledAt;


	// 팩토리 메서드
	public static EventHandledEntity of(String eventId, String consumerGroup) {
		EventHandledEntity entity = new EventHandledEntity();
		entity.eventId = eventId;
		entity.consumerGroup = consumerGroup;
		entity.handledAt = LocalDateTime.now();
		return entity;
	}

}
