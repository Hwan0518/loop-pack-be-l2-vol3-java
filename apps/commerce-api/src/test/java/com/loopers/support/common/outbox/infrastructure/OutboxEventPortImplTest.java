package com.loopers.support.common.outbox.infrastructure;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.support.common.event.EventType;
import com.loopers.support.common.outbox.application.port.OutboxEventPort;
import com.loopers.support.common.outbox.infrastructure.entity.OutboxEventApiEntity;
import com.loopers.support.common.outbox.infrastructure.jpa.OutboxEventApiJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("OutboxEventPortImpl 통합 테스트")
class OutboxEventPortImplTest {

	@Autowired
	private OutboxEventPort outboxEventPort;

	@Autowired
	private OutboxEventApiJpaRepository outboxEventApiJpaRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Test
	@DisplayName("[findRetryableEvents()] PENDING + FAILED(retry<3)만 생성 순서대로 반환. PUBLISHED/DEAD/재시도 소진은 제외")
	void findRetryableEvents_returnsOnlyRetryableEventsInCreatedOrder() throws Exception {
		// Arrange
		OutboxEventApiEntity pendingFirst = savePending("aggregate-1");
		Thread.sleep(5);
		OutboxEventApiEntity failedRetryable = saveFailed("aggregate-2", 1);
		Thread.sleep(5);
		OutboxEventApiEntity published = savePublished("aggregate-3");
		Thread.sleep(5);
		OutboxEventApiEntity dead = saveDead("aggregate-4");
		Thread.sleep(5);
		OutboxEventApiEntity failedExhausted = saveFailed("aggregate-5", 3);
		Thread.sleep(5);
		OutboxEventApiEntity pendingSecond = savePending("aggregate-6");

		// Act
		var events = outboxEventPort.findRetryableEvents(10);

		// Assert
		assertThat(events)
			.extracting(event -> event.aggregateId())
			.containsExactly(
				pendingFirst.getAggregateId(),
				failedRetryable.getAggregateId(),
				pendingSecond.getAggregateId()
			);
		assertThat(events)
			.extracting(event -> event.status())
			.containsExactly("PENDING", "FAILED", "PENDING");
		assertThat(events)
			.extracting(event -> event.retryCount())
			.containsExactly(0, 1, 0);
		assertThat(events)
			.extracting(event -> event.aggregateId())
			.doesNotContain(
				published.getAggregateId(),
				dead.getAggregateId(),
				failedExhausted.getAggregateId()
			);
	}


	@Test
	@DisplayName("[markPublished()] 존재하는 Outbox 이벤트 -> status=PUBLISHED, publishedAt 설정")
	void markPublished_updatesStatusAndPublishedAt() {
		// Arrange
		OutboxEventApiEntity entity = savePending("published-1");

		// Act
		outboxEventPort.markPublished(entity.getId());

		// Assert
		OutboxEventApiEntity updated = outboxEventApiJpaRepository.findById(entity.getId())
			.orElseThrow(() -> new IllegalStateException("outbox event not found"));
		assertAll(
			() -> assertThat(updated.getStatus()).isEqualTo("PUBLISHED"),
			() -> assertThat(updated.getPublishedAt()).isNotNull()
		);
	}


	@Test
	@DisplayName("[markFailed()] 존재하는 Outbox 이벤트 -> status=FAILED, retryCount 1 증가")
	void markFailed_updatesStatusAndRetryCount() {
		// Arrange
		OutboxEventApiEntity entity = savePending("failed-1");

		// Act
		outboxEventPort.markFailed(entity.getId());

		// Assert
		OutboxEventApiEntity updated = outboxEventApiJpaRepository.findById(entity.getId())
			.orElseThrow(() -> new IllegalStateException("outbox event not found"));
		assertAll(
			() -> assertThat(updated.getStatus()).isEqualTo("FAILED"),
			() -> assertThat(updated.getRetryCount()).isEqualTo(1)
		);
	}


	@Test
	@DisplayName("[markDead()] 존재하는 Outbox 이벤트 -> status=DEAD")
	void markDead_updatesStatus() {
		// Arrange
		OutboxEventApiEntity entity = savePending("dead-1");

		// Act
		outboxEventPort.markDead(entity.getId());

		// Assert
		OutboxEventApiEntity updated = outboxEventApiJpaRepository.findById(entity.getId())
			.orElseThrow(() -> new IllegalStateException("outbox event not found"));
		assertThat(updated.getStatus()).isEqualTo("DEAD");
	}


	private OutboxEventApiEntity savePending(String aggregateId) {
		outboxEventPort.save(EventType.COUPON_ISSUE_REQUESTED, aggregateId, aggregateId, "{\"payload\":true}");
		return findLatestByAggregateId(aggregateId);
	}


	private OutboxEventApiEntity savePublished(String aggregateId) {
		OutboxEventApiEntity entity = savePending(aggregateId);
		entity.markPublished();
		return outboxEventApiJpaRepository.save(entity);
	}


	private OutboxEventApiEntity saveDead(String aggregateId) {
		OutboxEventApiEntity entity = savePending(aggregateId);
		entity.markDead();
		return outboxEventApiJpaRepository.save(entity);
	}


	private OutboxEventApiEntity saveFailed(String aggregateId, int retryCount) {
		OutboxEventApiEntity entity = savePending(aggregateId);
		for (int i = 0; i < retryCount; i++) {
			entity.markFailed();
		}
		return outboxEventApiJpaRepository.save(entity);
	}


	private OutboxEventApiEntity findLatestByAggregateId(String aggregateId) {
		List<OutboxEventApiEntity> events = outboxEventApiJpaRepository.findAll().stream()
			.filter(event -> aggregateId.equals(event.getAggregateId()))
			.toList();
		return events.stream()
			.reduce((first, second) -> second)
			.orElseThrow(() -> new IllegalStateException("outbox event not found: " + aggregateId));
	}
}
