package com.loopers.ordering.order.application.service;


import com.loopers.ordering.order.application.dto.out.*;
import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.repository.OrderQueryRepository;
import com.loopers.ordering.order.domain.repository.vo.PageCriteria;
import com.loopers.ordering.order.domain.repository.vo.PageResult;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;


@Service
@RequiredArgsConstructor
public class OrderQueryService {

	// repository
	private final OrderQueryRepository orderQueryRepository;


	/**
	 * 주문 조회 서비스
	 * 1. ID로 주문 조회
	 * 2. ID + 사용자 ID로 주문 조회 (본인 주문만)
	 * 3. 사용자별 주문 목록 조회
	 * 4. 전체 주문 목록 조회 (관리자)
	 */

	// 1. ID로 주문 조회
	@Transactional(readOnly = true)
	public Order findById(Long id) {
		return orderQueryRepository.findById(id)
			.orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));
	}


	// 2. ID + 사용자 ID로 주문 조회 (본인 주문만)
	@Transactional(readOnly = true)
	public Order findByIdAndUserId(Long id, Long userId) {

		// 주문 조회
		Order order = findById(id);

		// 본인 주문 검증
		if (!order.getUserId().equals(userId)) {
			throw new CoreException(ErrorType.ORDER_NOT_FOUND);
		}

		return order;
	}


	// 3. 사용자별 주문 목록 조회 (날짜 필터)
	@Transactional(readOnly = true)
	public OrderPageOutDto getOrdersByUserId(Long userId, int page, int size, LocalDate startDate, LocalDate endDate) {

		// 페이지 조건 생성
		PageCriteria pageCriteria = new PageCriteria(page, size);

		// 사용자별 주문 목록 조회 (날짜 필터 포함)
		PageResult<Order> result = orderQueryRepository.findByUserId(userId, startDate, endDate, pageCriteria);

		// DTO 변환
		return new OrderPageOutDto(
			result.content().stream().map(OrderOutDto::from).toList(),
			result.page(),
			result.size(),
			result.totalElements()
		);
	}


	// 4. 전체 주문 목록 조회 (관리자)
	@Transactional(readOnly = true)
	public AdminOrderPageOutDto getAllOrders(int page, int size) {

		// 페이지 조건 생성
		PageCriteria pageCriteria = new PageCriteria(page, size);

		// 전체 주문 목록 조회
		PageResult<Order> result = orderQueryRepository.findAll(pageCriteria);

		// DTO 변환
		return new AdminOrderPageOutDto(
			result.content().stream().map(AdminOrderOutDto::from).toList(),
			result.page(),
			result.size(),
			result.totalElements()
		);
	}

}
