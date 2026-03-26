package com.loopers.support.common.outbox.infrastructure.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


/**
 * Outbox 이벤트 엔티티 (commerce-api 전용 — outbox_event_api 테이블)
 */

@Entity
@Table(name = "outbox_event_api", indexes = {
	@Index(name = "idx_outbox_status_created", columnList = "status, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventApiEntity {

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
	public static OutboxEventApiEntity of(String aggregateType, String aggregateId, String eventType,
		String topic, String partitionKey, String payload) {
		OutboxEventApiEntity entity = new OutboxEventApiEntity();
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


	// 발행 완료
	public void markPublished() {
		this.status = "PUBLISHED";
		this.publishedAt = LocalDateTime.now();
	}

	// 실패 (retryCount 증가)
	public void markFailed() {
		this.status = "FAILED";
		this.retryCount++;
	}

	// DEAD (재시도 소진)
	public void markDead() {
		this.status = "DEAD";
	}

}
