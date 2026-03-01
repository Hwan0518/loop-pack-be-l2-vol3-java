package com.loopers.ordering.order.infrastructure.mapper;


import com.loopers.ordering.order.domain.model.IdempotencyKey;
import com.loopers.ordering.order.infrastructure.entity.IdempotencyKeyEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@DisplayName("IdempotencyKeyEntityMapper 테스트")
class IdempotencyKeyEntityMapperTest {

	private IdempotencyKeyEntityMapper mapper;

	@BeforeEach
	void setUp() {
		mapper = new IdempotencyKeyEntityMapper();
	}


	@Test
	@DisplayName("[toEntity()] IdempotencyKey -> IdempotencyKeyEntity 변환. userId, requestId, orderId 일치")
	void toEntitySuccess() {
		// Arrange
		IdempotencyKey key = IdempotencyKey.create(100L, "req-123", 10L);

		// Act
		IdempotencyKeyEntity entity = mapper.toEntity(key);

		// Assert
		assertAll(
			() -> assertThat(entity.getUserId()).isEqualTo(100L),
			() -> assertThat(entity.getRequestId()).isEqualTo("req-123"),
			() -> assertThat(entity.getOrderId()).isEqualTo(10L)
		);
	}


	@Test
	@DisplayName("[toDomain()] IdempotencyKeyEntity -> IdempotencyKey 변환. 모든 필드 일치")
	void toDomainSuccess() {
		// Arrange
		IdempotencyKeyEntity entity = IdempotencyKeyEntity.of(100L, "req-123", 10L);

		// Act
		IdempotencyKey key = mapper.toDomain(entity);

		// Assert
		assertAll(
			() -> assertThat(key.getUserId()).isEqualTo(100L),
			() -> assertThat(key.getRequestId()).isEqualTo("req-123"),
			() -> assertThat(key.getOrderId()).isEqualTo(10L)
		);
	}

}
