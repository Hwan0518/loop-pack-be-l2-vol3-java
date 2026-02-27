package com.loopers.ordering.order.infrastructure.repository;


import com.loopers.ordering.order.domain.model.IdempotencyKey;
import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.OrderItem;
import com.loopers.ordering.order.domain.repository.IdempotencyKeyCommandRepository;
import com.loopers.ordering.order.domain.repository.IdempotencyKeyQueryRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("IdempotencyKeyQueryRepository 통합 테스트")
class IdempotencyKeyQueryRepositoryTest {

	@Autowired
	private IdempotencyKeyCommandRepository idempotencyKeyCommandRepository;

	@Autowired
	private IdempotencyKeyQueryRepository idempotencyKeyQueryRepository;

	@Autowired
	private OrderCommandRepository orderCommandRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Test
	@DisplayName("[IdempotencyKeyQueryRepository.findByUserIdAndRequestId()] 존재하는 키 -> IdempotencyKey 반환")
	void findByUserIdAndRequestIdExists() {
		// Arrange
		Order savedOrder = saveTestOrder();
		IdempotencyKey savedKey = idempotencyKeyCommandRepository.save(
			IdempotencyKey.create(100L, "req-001", savedOrder.getId())
		);

		// Act
		Optional<IdempotencyKey> result = idempotencyKeyQueryRepository.findByUserIdAndRequestId(100L, "req-001");

		// Assert
		assertAll(
			() -> assertThat(result).isPresent(),
			() -> assertThat(result.get().getId()).isEqualTo(savedKey.getId()),
			() -> assertThat(result.get().getUserId()).isEqualTo(100L),
			() -> assertThat(result.get().getRequestId()).isEqualTo("req-001"),
			() -> assertThat(result.get().getOrderId()).isEqualTo(savedOrder.getId())
		);
	}


	@Test
	@DisplayName("[IdempotencyKeyQueryRepository.findByUserIdAndRequestId()] 존재하지 않는 키 -> Optional.empty 반환")
	void findByUserIdAndRequestIdNotExists() {
		// Arrange - 아무 데이터 없음

		// Act
		Optional<IdempotencyKey> result = idempotencyKeyQueryRepository.findByUserIdAndRequestId(100L, "non-existent");

		// Assert
		assertThat(result).isEmpty();
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
