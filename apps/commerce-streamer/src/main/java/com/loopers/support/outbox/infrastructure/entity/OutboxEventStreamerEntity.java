package com.loopers.support.outbox.infrastructure.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


/**
 * Outbox 이벤트 엔티티 (commerce-streamer 전용 — outbox_event_streamer 테이블)
 * - 스키마는 commerce-api의 OutboxEventApiEntity와 동일, 테이블명만 다름
 */

@Entity
@Table(name = "outbox_event_streamer", indexes = {
	@Index(name = "idx_outbox_str_status_created", columnList = "status, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventStreamerEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String aggregateType;

	@Column(nullable = false, length = 100)
	private String aggregateId;

	@Column(nullable = false, length = 100)
	private String eventType;

	@Column(nullable = false, length = 100)
	private String topic;

	@Column(nullable = false, length = 100)
	private String partitionKey;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String payload;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(nullable = false)
	private int retryCount;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	private LocalDateTime publishedAt;


	// 팩토리 메서드 — PENDING 상태로 생성
	public static OutboxEventStreamerEntity of(String aggregateType, String aggregateId, String eventType,
		String topic, String partitionKey, String payload) {
		OutboxEventStreamerEntity entity = new OutboxEventStreamerEntity();
		entity.aggregateType = aggregateType;
		entity.aggregateId = aggregateId;
		entity.eventType = eventType;
		entity.topic = topic;
		entity.partitionKey = partitionKey;
		entity.payload = payload;
		entity.status = "PENDING";
		entity.retryCount = 0;
		entity.createdAt = LocalDateTime.now();
		return entity;
	}

	public void markPublished() {
		this.status = "PUBLISHED";
		this.publishedAt = LocalDateTime.now();
	}

	public void markFailed() {
		this.status = "FAILED";
		this.retryCount++;
	}

	public void markDead() {
		this.status = "DEAD";
	}

}
