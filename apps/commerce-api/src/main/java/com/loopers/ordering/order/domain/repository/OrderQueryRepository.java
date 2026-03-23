package com.loopers.ordering.order.domain.repository;


import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.repository.vo.PageCriteria;
import com.loopers.ordering.order.domain.repository.vo.PageResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public interface OrderQueryRepository {

	/**
	 * 주문 조회 리포지토리
	 * 1. ID로 주문 조회
	 * 2. 사용자별 주문 목록 조회 (페이지네이션, 날짜 필터)
	 * 3. 전체 주문 목록 조회 (관리자, 페이지네이션)
	 * 4. userId + requestId로 주문 조회 (멱등성 확인)
	 * 5. ID로 주문 조회 (비관적 쓰기 락 — 결제 시 동시 상태 변경 방지)
	 * 6. 만료 대상 주문 목록 조회 (PENDING_PAYMENT/PAYMENT_FAILED + 만료시간 초과)
	 */

	// 1. ID로 주문 조회
	Optional<Order> findById(Long id);

	// 2. 사용자별 주문 목록 조회 (페이지네이션, 날짜 필터)
	PageResult<Order> findByUserId(Long userId, LocalDate startDate, LocalDate endDate, PageCriteria pageCriteria);

	// 3. 전체 주문 목록 조회 (관리자, 페이지네이션)
	PageResult<Order> findAll(PageCriteria pageCriteria);

	// 4. userId + requestId로 주문 조회 (멱등성 확인)
	Optional<Order> findByUserIdAndRequestId(Long userId, String requestId);

	// 5. ID로 주문 조회 (비관적 쓰기 락 — 결제 시 동시 상태 변경 방지)
	Optional<Order> findByIdForUpdate(Long id);

	// 6. 만료 대상 주문 목록 조회 (PENDING_PAYMENT/PAYMENT_FAILED + 만료시간 초과)
	List<Order> findExpirableOrders(LocalDateTime expirationThreshold);

}
