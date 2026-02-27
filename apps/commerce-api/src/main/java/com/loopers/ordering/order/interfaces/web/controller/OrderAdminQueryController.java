package com.loopers.ordering.order.interfaces.web.controller;


import com.loopers.ordering.order.application.dto.out.AdminOrderDetailOutDto;
import com.loopers.ordering.order.application.dto.out.AdminOrderPageOutDto;
import com.loopers.ordering.order.application.facade.OrderQueryFacade;
import com.loopers.ordering.order.interfaces.web.response.AdminOrderDetailResponse;
import com.loopers.ordering.order.interfaces.web.response.AdminOrderPageResponse;
import com.loopers.support.common.AdminHeaderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api-admin/v1/orders")
@RequiredArgsConstructor
public class OrderAdminQueryController {

	// facade
	private final OrderQueryFacade orderQueryFacade;


	/**
	 * 주문 관리자 조회 컨트롤러 (LDAP 인증 필요)
	 * 1. 관리자 주문 목록 조회 (페이지네이션)
	 * 2. 관리자 주문 상세 조회
	 */

	// 1. 관리자 주문 목록 조회 (페이지네이션)
	@GetMapping
	public ResponseEntity<AdminOrderPageResponse> getAdminOrders(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// 주문 목록 조회
		AdminOrderPageOutDto outDto = orderQueryFacade.getAdminOrders(page, size);

		// 응답 변환
		AdminOrderPageResponse response = AdminOrderPageResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}


	// 2. 관리자 주문 상세 조회
	@GetMapping("/{orderId}")
	public ResponseEntity<AdminOrderDetailResponse> getAdminOrder(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@PathVariable Long orderId
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// 주문 상세 조회
		AdminOrderDetailOutDto outDto = orderQueryFacade.getAdminOrder(orderId);

		// 응답 변환
		AdminOrderDetailResponse response = AdminOrderDetailResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}

}
