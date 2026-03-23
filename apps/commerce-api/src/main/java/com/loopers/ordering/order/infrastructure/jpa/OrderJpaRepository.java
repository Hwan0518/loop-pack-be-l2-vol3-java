package com.loopers.ordering.order.infrastructure.jpa;


import com.loopers.ordering.order.domain.model.enums.OrderStatus;
import com.loopers.ordering.order.infrastructure.entity.OrderEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;


public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {

	/**
	 * 주문 JPA 리포지토리
	 * 1. 사용자별 주문 목록 조회 (페이지네이션)
	 * 2. 사용자별 주문 목록 조회 (페이지네이션, 날짜 필터)
	 * 3. userId + requestId로 주문 조회 (멱등성 확인)
	 * 4. ID로 주문 조회 (비관적 쓰기 락)
	 * 5. 주문 상태 변경 (JPQL UPDATE — CAS 패턴)
	 * 6. 만료 대상 주문 목록 조회 (PENDING_PAYMENT/PAYMENT_FAILED + 만료시간 초과)
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

	// 3. userId + requestId로 주문 조회 (멱등성 확인)
	Optional<OrderEntity> findByUserIdAndRequestId(Long userId, String requestId);

	// 4. ID로 주문 조회 (비관적 쓰기 락)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT o FROM OrderEntity o WHERE o.id = :id")
	Optional<OrderEntity> findByIdForUpdate(@Param("id") Long id);

	// 5. 주문 상태 변경 (JPQL UPDATE — CAS 패턴: 현재 status 검증 포함)
	@org.springframework.data.jpa.repository.Modifying
	@Query("UPDATE OrderEntity o SET o.status = :newStatus WHERE o.id = :id AND o.status = :currentStatus")
	int updateStatus(@Param("id") Long id, @Param("currentStatus") OrderStatus currentStatus, @Param("newStatus") OrderStatus newStatus);

	// 6. 만료 대상 주문 목록 조회 (PENDING_PAYMENT/PAYMENT_FAILED + 만료시간 초과)
	@Query("SELECT o FROM OrderEntity o WHERE o.status IN :statuses AND o.createdAt < :threshold")
	List<OrderEntity> findByStatusInAndCreatedAtBefore(
		@Param("statuses") List<OrderStatus> statuses,
		@Param("threshold") ZonedDateTime threshold
	);

}
