package com.loopers.ordering.order.interfaces.web.controller;


import com.loopers.ordering.order.application.dto.out.OrderDetailOutDto;
import com.loopers.ordering.order.application.dto.out.OrderPageOutDto;
import com.loopers.ordering.order.application.facade.OrderQueryFacade;
import com.loopers.ordering.order.interfaces.web.response.OrderDetailResponse;
import com.loopers.ordering.order.interfaces.web.response.OrderPageResponse;
import com.loopers.support.common.auth.AuthenticationResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;


@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderQueryController {

	// facade
	private final OrderQueryFacade orderQueryFacade;
	// auth
	private final AuthenticationResolver authenticationResolver;


	/**
	 * 주문 조회 컨트롤러 (사용자 인증 필요)
	 * 1. 내 주문 목록 조회 (페이지네이션)
	 * 2. 내 주문 상세 조회
	 */

	// 1. 내 주문 목록 조회 (페이지네이션, 날짜 필터)
	@GetMapping
	public ResponseEntity<OrderPageResponse> getOrders(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
	) {

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// 주문 목록 조회 (날짜 필터 포함)
		OrderPageOutDto outDto = orderQueryFacade.getOrders(userId, page, size, startDate, endDate);

		// 응답 변환
		OrderPageResponse response = OrderPageResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}


	// 2. 내 주문 상세 조회
	@GetMapping("/{orderId}")
	public ResponseEntity<OrderDetailResponse> getOrder(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@PathVariable Long orderId
	) {

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// 주문 상세 조회
		OrderDetailOutDto outDto = orderQueryFacade.getOrder(userId, orderId);

		// 응답 변환
		OrderDetailResponse response = OrderDetailResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}

}
