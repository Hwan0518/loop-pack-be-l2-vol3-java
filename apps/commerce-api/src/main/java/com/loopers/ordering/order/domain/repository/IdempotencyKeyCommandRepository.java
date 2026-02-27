package com.loopers.ordering.order.domain.repository;


import com.loopers.ordering.order.domain.model.IdempotencyKey;


public interface IdempotencyKeyCommandRepository {

	/**
	 * 멱등성 키 명령 리포지토리
	 * 1. 멱등성 키 저장
	 */

	// 1. 멱등성 키 저장
	IdempotencyKey save(IdempotencyKey idempotencyKey);

}
