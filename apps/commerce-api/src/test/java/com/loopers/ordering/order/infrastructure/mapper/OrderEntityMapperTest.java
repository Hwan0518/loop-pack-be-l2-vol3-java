package com.loopers.ordering.order.infrastructure.mapper;


import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.OrderItem;
import com.loopers.ordering.order.infrastructure.entity.OrderEntity;
import com.loopers.ordering.order.infrastructure.entity.OrderItemEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@DisplayName("OrderEntityMapper 테스트")
class OrderEntityMapperTest {

	private OrderEntityMapper mapper;

	@BeforeEach
	void setUp() {
		OrderItemEntityMapper itemMapper = new OrderItemEntityMapper();
		mapper = new OrderEntityMapper(itemMapper);
	}


	@Test
	@DisplayName("[toEntity()] Order -> OrderEntity 변환. userId, totalPrice 일치")
	void toEntitySuccess() {
		// Arrange
		List<OrderItem> items = List.of(
			OrderItem.create(1L, "나이키 에어맥스", new BigDecimal("100000"), 2L)
		);
		Order order = Order.create(100L, new BigDecimal("200000"), items);

		// Act
		OrderEntity entity = mapper.toEntity(order);

		// Assert
		assertAll(
			() -> assertThat(entity.getUserId()).isEqualTo(100L),
			() -> assertThat(entity.getTotalPrice()).isEqualByComparingTo(new BigDecimal("200000"))
		);
	}


	@Test
	@DisplayName("[toDomain()] OrderEntity + OrderItemEntity 목록 -> Order 변환. 모든 필드 및 항목 일치")
	void toDomainSuccess() {
		// Arrange
		OrderEntity orderEntity = OrderEntity.of(100L, new BigDecimal("200000"));
		List<OrderItemEntity> itemEntities = List.of(
			OrderItemEntity.of(1L, 1L, "나이키 에어맥스", new BigDecimal("100000"), 2L)
		);

		// Act
		Order order = mapper.toDomain(orderEntity, itemEntities);

		// Assert
		assertAll(
			() -> assertThat(order.getUserId()).isEqualTo(100L),
			() -> assertThat(order.getTotalPrice()).isEqualByComparingTo(new BigDecimal("200000")),
			() -> assertThat(order.getItems()).hasSize(1)
		);
	}

}
