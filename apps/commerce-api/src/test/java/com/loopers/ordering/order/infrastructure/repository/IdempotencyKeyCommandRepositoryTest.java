package com.loopers.ordering.order.infrastructure.repository;


import com.loopers.ordering.order.domain.model.IdempotencyKey;
import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.OrderItem;
import com.loopers.ordering.order.domain.repository.IdempotencyKeyCommandRepository;
import com.loopers.ordering.order.domain.repository.OrderCommandRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("IdempotencyKeyCommandRepository 통합 테스트")
class IdempotencyKeyCommandRepositoryTest {

	@Autowired
	private IdempotencyKeyCommandRepository idempotencyKeyCommandRepository;

	@Autowired
	private OrderCommandRepository orderCommandRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Test
	@DisplayName("[IdempotencyKeyCommandRepository.save()] 유효한 멱등성 키 저장 -> ID가 할당된 IdempotencyKey 반환")
	void saveIdempotencyKey() {
		// Arrange
		Order savedOrder = saveTestOrder();
		IdempotencyKey key = IdempotencyKey.create(100L, "req-001", savedOrder.getId());

		// Act
		IdempotencyKey savedKey = idempotencyKeyCommandRepository.save(key);

		// Assert
		assertAll(
			() -> assertThat(savedKey.getId()).isNotNull(),
			() -> assertThat(savedKey.getUserId()).isEqualTo(100L),
			() -> assertThat(savedKey.getRequestId()).isEqualTo("req-001"),
			() -> assertThat(savedKey.getOrderId()).isEqualTo(savedOrder.getId())
		);
	}


	@Test
	@DisplayName("[IdempotencyKeyCommandRepository.save()] 동일 (userId, requestId) 중복 저장 -> 유니크 제약 위반 예외")
	void saveDuplicateIdempotencyKey() {
		// Arrange
		Order savedOrder = saveTestOrder();
		IdempotencyKey key1 = IdempotencyKey.create(100L, "req-001", savedOrder.getId());
		idempotencyKeyCommandRepository.save(key1);

		IdempotencyKey key2 = IdempotencyKey.create(100L, "req-001", savedOrder.getId());

		// Act & Assert
		assertThatThrownBy(() -> idempotencyKeyCommandRepository.save(key2))
			.isInstanceOf(Exception.class);
	}


	/**
	 * 테스트 헬퍼
	 * 1. 테스트용 주문 저장
	 */

	// 1. 테스트용 주문 저장
	private Order saveTestOrder() {
		OrderItem item = OrderItem.create(1L, "상품A", new BigDecimal("10000"), 1L);
		return orderCommandRepository.save(
			Order.create(100L, new BigDecimal("10000"), List.of(item))
		);
	}

}
