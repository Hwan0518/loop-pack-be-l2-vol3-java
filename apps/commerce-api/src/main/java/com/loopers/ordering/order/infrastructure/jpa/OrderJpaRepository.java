package com.loopers.ordering.order.infrastructure.jpa;


import com.loopers.ordering.order.infrastructure.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;


public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {

	/**
	 * 주문 JPA 리포지토리
	 * 1. 사용자별 주문 목록 조회 (페이지네이션)
	 * 2. 사용자별 주문 목록 조회 (페이지네이션, 날짜 필터)
	 */

	// 1. 사용자별 주문 목록 조회 (페이지네이션)
	Page<OrderEntity> findByUserId(Long userId, Pageable pageable);

	// 2. 사용자별 주문 목록 조회 (페이지네이션, 날짜 필터)
	@Query("SELECT o FROM OrderEntity o WHERE o.userId = :userId AND o.createdAt >= :startDateTime AND o.createdAt < :endDateTime")
	Page<OrderEntity> findByUserIdAndCreatedAtInRange(
		@Param("userId") Long userId,
		@Param("startDateTime") ZonedDateTime startDateTime,
		@Param("endDateTime") ZonedDateTime endDateTime,
		Pageable pageable
	);

}
