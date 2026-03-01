package com.loopers.ordering.order.domain.repository;


import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.repository.vo.PageCriteria;
import com.loopers.ordering.order.domain.repository.vo.PageResult;

import java.time.LocalDate;
import java.util.Optional;


public interface OrderQueryRepository {

	/**
	 * 주문 조회 리포지토리
	 * 1. ID로 주문 조회
	 * 2. 사용자별 주문 목록 조회 (페이지네이션)
	 * 3. 전체 주문 목록 조회 (관리자, 페이지네이션)
	 */

	// 1. ID로 주문 조회
	Optional<Order> findById(Long id);

	// 2. 사용자별 주문 목록 조회 (페이지네이션, 날짜 필터)
	PageResult<Order> findByUserId(Long userId, LocalDate startDate, LocalDate endDate, PageCriteria pageCriteria);

	// 3. 전체 주문 목록 조회 (관리자, 페이지네이션)
	PageResult<Order> findAll(PageCriteria pageCriteria);

}
