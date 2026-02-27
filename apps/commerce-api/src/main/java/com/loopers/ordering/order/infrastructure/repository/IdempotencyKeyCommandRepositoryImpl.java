package com.loopers.ordering.order.infrastructure.repository;


import com.loopers.ordering.order.domain.model.IdempotencyKey;
import com.loopers.ordering.order.domain.repository.IdempotencyKeyCommandRepository;
import com.loopers.ordering.order.infrastructure.entity.IdempotencyKeyEntity;
import com.loopers.ordering.order.infrastructure.jpa.IdempotencyKeyJpaRepository;
import com.loopers.ordering.order.infrastructure.mapper.IdempotencyKeyEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;


@Repository
@RequiredArgsConstructor
public class IdempotencyKeyCommandRepositoryImpl implements IdempotencyKeyCommandRepository {

	// jpa
	private final IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;
	// mapper
	private final IdempotencyKeyEntityMapper idempotencyKeyMapper;


	/**
	 * 멱등성 키 명령 리포지토리 구현체
	 * 1. 멱등성 키 저장
	 */

	// 1. 멱등성 키 저장
	@Override
	public IdempotencyKey save(IdempotencyKey idempotencyKey) {

		// 엔티티로 변환
		IdempotencyKeyEntity entity = idempotencyKeyMapper.toEntity(idempotencyKey);

		// 저장
		IdempotencyKeyEntity savedEntity = idempotencyKeyJpaRepository.save(entity);

		// 도메인 변환 후 반환
		return idempotencyKeyMapper.toDomain(savedEntity);
	}

}
