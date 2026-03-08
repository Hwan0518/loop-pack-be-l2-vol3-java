package com.loopers.ordering.order.infrastructure.repository;


import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.OrderItem;
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
import static org.junit.jupiter.api.Assertions.assertAll;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("OrderCommandRepository 통합 테스트")
class OrderCommandRepositoryTest {

	@Autowired
	private OrderCommandRepository orderCommandRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Test
	@DisplayName("[OrderCommandRepository.save()] 유효한 주문 저장 -> ID가 할당된 Order 반환. 주문 항목도 함께 저장")
	void saveOrder() {
		// Arrange
		OrderItem item = OrderItem.create(1L, "상품A", new BigDecimal("10000"), 2L);
		Order order = Order.create(100L, "req-cmd-1", new BigDecimal("20000"), BigDecimal.ZERO, List.of(item), null);

		// Act
		Order savedOrder = orderCommandRepository.save(order);

		// Assert
		assertAll(
			() -> assertThat(savedOrder.getId()).isNotNull(),
			() -> assertThat(savedOrder.getUserId()).isEqualTo(100L),
			() -> assertThat(savedOrder.getTotalPrice()).isEqualByComparingTo(new BigDecimal("20000")),
			() -> assertThat(savedOrder.getItems()).hasSize(1),
			() -> assertThat(savedOrder.getItems().get(0).getId()).isNotNull(),
			() -> assertThat(savedOrder.getItems().get(0).getProductId()).isEqualTo(1L),
			() -> assertThat(savedOrder.getItems().get(0).getSnapshotName().value()).isEqualTo("상품A"),
			() -> assertThat(savedOrder.getItems().get(0).getSnapshotPrice().value()).isEqualByComparingTo(new BigDecimal("10000")),
			() -> assertThat(savedOrder.getItems().get(0).getQuantity()).isEqualTo(2L)
		);
	}


	@Test
	@DisplayName("[OrderCommandRepository.save()] 다수 주문항목 포함 주문 저장 -> 모든 주문항목 저장 성공")
	void saveOrderWithMultipleItems() {
		// Arrange
		OrderItem item1 = OrderItem.create(1L, "상품A", new BigDecimal("10000"), 2L);
		OrderItem item2 = OrderItem.create(2L, "상품B", new BigDecimal("20000"), 1L);
		OrderItem item3 = OrderItem.create(3L, "상품C", new BigDecimal("5000"), 3L);
		Order order = Order.create(100L, "req-cmd-2", new BigDecimal("55000"), BigDecimal.ZERO, List.of(item1, item2, item3), null);

		// Act
		Order savedOrder = orderCommandRepository.save(order);

		// Assert
		assertAll(
			() -> assertThat(savedOrder.getId()).isNotNull(),
			() -> assertThat(savedOrder.getItems()).hasSize(3),
			() -> assertThat(savedOrder.getItems()).allSatisfy(item ->
				assertThat(item.getId()).isNotNull()
			)
		);
	}

}
