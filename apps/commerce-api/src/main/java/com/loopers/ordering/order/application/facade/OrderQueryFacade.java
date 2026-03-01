package com.loopers.ordering.order.application.facade;


import com.loopers.ordering.order.application.dto.out.AdminOrderDetailOutDto;
import com.loopers.ordering.order.application.dto.out.AdminOrderPageOutDto;
import com.loopers.ordering.order.application.dto.out.OrderDetailOutDto;
import com.loopers.ordering.order.application.dto.out.OrderPageOutDto;
import com.loopers.ordering.order.application.service.OrderCheckoutCommandService;
import com.loopers.ordering.order.application.service.OrderQueryService;
import com.loopers.ordering.order.domain.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;


@Service
@RequiredArgsConstructor
public class OrderQueryFacade {

	// service
	private final OrderCheckoutCommandService orderCheckoutCommandService;
	private final OrderQueryService orderQueryService;


	/**
	 * 주문 조회 파사드
	 * 1. 내 주문 상세 조회
	 * 2. 내 주문 목록 조회 (페이지네이션)
	 * 3. 관리자 주문 상세 조회
	 * 4. 관리자 주문 목록 조회 (페이지네이션)
	 */

	// 1. 내 주문 상세 조회
	@Transactional(readOnly = true)
	public OrderDetailOutDto getOrder(String loginId, String password, Long orderId) {

		// 사용자 인증
		Long userId = orderCheckoutCommandService.authenticate(loginId, password);

		// 본인 주문 조회
		Order order = orderQueryService.findByIdAndUserId(orderId, userId);

		// DTO 변환
		return OrderDetailOutDto.from(order);
	}


	// 2. 내 주문 목록 조회 (페이지네이션, 날짜 필터)
	@Transactional(readOnly = true)
	public OrderPageOutDto getOrders(String loginId, String password, int page, int size, LocalDate startDate, LocalDate endDate) {

		// 사용자 인증
		Long userId = orderCheckoutCommandService.authenticate(loginId, password);

		// 사용자별 주문 목록 조회 (날짜 필터 포함)
		return orderQueryService.getOrdersByUserId(userId, page, size, startDate, endDate);
	}


	// 3. 관리자 주문 상세 조회
	@Transactional(readOnly = true)
	public AdminOrderDetailOutDto getAdminOrder(Long orderId) {

		// 주문 조회
		Order order = orderQueryService.findById(orderId);

		// DTO 변환
		return AdminOrderDetailOutDto.from(order);
	}


	// 4. 관리자 주문 목록 조회 (페이지네이션)
	@Transactional(readOnly = true)
	public AdminOrderPageOutDto getAdminOrders(int page, int size) {

		// 전체 주문 목록 조회
		return orderQueryService.getAllOrders(page, size);
	}

}
