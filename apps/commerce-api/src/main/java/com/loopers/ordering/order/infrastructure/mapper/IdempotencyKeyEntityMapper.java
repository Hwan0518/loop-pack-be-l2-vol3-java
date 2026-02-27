package com.loopers.ordering.order.infrastructure.mapper;


import com.loopers.ordering.order.domain.model.IdempotencyKey;
import com.loopers.ordering.order.infrastructure.entity.IdempotencyKeyEntity;
import org.springframework.stereotype.Component;


/**
 * 멱등성 키 엔티티 매퍼
 * 1. Domain -> Entity 변환
 * 2. Entity -> Domain 변환
 */
@Component
public class IdempotencyKeyEntityMapper {

	// 1. Domain -> Entity 변환
	public IdempotencyKeyEntity toEntity(IdempotencyKey idempotencyKey) {
		return IdempotencyKeyEntity.of(
			idempotencyKey.getUserId(),
			idempotencyKey.getRequestId(),
			idempotencyKey.getOrderId()
		);
	}


	// 2. Entity -> Domain 변환
	public IdempotencyKey toDomain(IdempotencyKeyEntity entity) {
		return IdempotencyKey.reconstruct(
			entity.getId(),
			entity.getUserId(),
			entity.getRequestId(),
			entity.getOrderId(),
			entity.getCreatedAt() != null ? entity.getCreatedAt().toLocalDateTime() : null
		);
	}

}
