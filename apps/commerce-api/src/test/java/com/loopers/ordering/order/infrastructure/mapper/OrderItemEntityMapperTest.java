package com.loopers.ordering.order.infrastructure.mapper;


import com.loopers.ordering.order.domain.model.OrderItem;
import com.loopers.ordering.order.domain.model.vo.SnapshotName;
import com.loopers.ordering.order.domain.model.vo.SnapshotPrice;
import com.loopers.ordering.order.infrastructure.entity.OrderItemEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@DisplayName("OrderItemEntityMapper 테스트")
class OrderItemEntityMapperTest {

	private OrderItemEntityMapper mapper;

	@BeforeEach
	void setUp() {
		mapper = new OrderItemEntityMapper();
	}


	@Test
	@DisplayName("[toEntity()] OrderItem -> OrderItemEntity 변환. orderId, productId, snapshotName, snapshotPrice, quantity 일치")
	void toEntitySuccess() {
		// Arrange
		OrderItem orderItem = OrderItem.create(1L, "나이키 에어맥스", new BigDecimal("100000"), 2L);

		// Act
		OrderItemEntity entity = mapper.toEntity(orderItem, 10L);

		// Assert
		assertAll(
			() -> assertThat(entity.getOrderId()).isEqualTo(10L),
			() -> assertThat(entity.getProductId()).isEqualTo(1L),
			() -> assertThat(entity.getSnapshotName()).isEqualTo("나이키 에어맥스"),
			() -> assertThat(entity.getSnapshotPrice()).isEqualByComparingTo(new BigDecimal("100000")),
			() -> assertThat(entity.getQuantity()).isEqualTo(2L)
		);
	}


	@Test
	@DisplayName("[toEntities()] OrderItem 목록 -> OrderItemEntity 목록 변환. 크기 일치")
	void toEntitiesSuccess() {
		// Arrange
		List<OrderItem> items = List.of(
			OrderItem.create(1L, "나이키 에어맥스", new BigDecimal("100000"), 2L),
			OrderItem.create(2L, "아디다스 울트라부스트", new BigDecimal("200000"), 1L)
		);

		// Act
		List<OrderItemEntity> entities = mapper.toEntities(items, 10L);

		// Assert
		assertAll(
			() -> assertThat(entities).hasSize(2),
			() -> assertThat(entities.get(0).getOrderId()).isEqualTo(10L),
			() -> assertThat(entities.get(1).getOrderId()).isEqualTo(10L)
		);
	}


	@Test
	@DisplayName("[toDomain()] OrderItemEntity -> OrderItem 변환. 모든 필드 일치")
	void toDomainSuccess() {
		// Arrange
		OrderItemEntity entity = OrderItemEntity.of(10L, 1L, "나이키 에어맥스", new BigDecimal("100000"), 2L);

		// Act
		OrderItem orderItem = mapper.toDomain(entity);

		// Assert
		assertAll(
			() -> assertThat(orderItem.getProductId()).isEqualTo(1L),
			() -> assertThat(orderItem.getSnapshotName().value()).isEqualTo("나이키 에어맥스"),
			() -> assertThat(orderItem.getSnapshotPrice().value()).isEqualByComparingTo(new BigDecimal("100000")),
			() -> assertThat(orderItem.getQuantity()).isEqualTo(2L)
		);
	}


	@Test
	@DisplayName("[toDomains()] OrderItemEntity 목록 -> OrderItem 목록 변환. 크기 일치")
	void toDomainsSuccess() {
		// Arrange
		List<OrderItemEntity> entities = List.of(
			OrderItemEntity.of(10L, 1L, "나이키 에어맥스", new BigDecimal("100000"), 2L),
			OrderItemEntity.of(10L, 2L, "아디다스 울트라부스트", new BigDecimal("200000"), 1L)
		);

		// Act
		List<OrderItem> items = mapper.toDomains(entities);

		// Assert
		assertThat(items).hasSize(2);
	}

}
