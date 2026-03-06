package com.loopers.ordering.order.interfaces.web.controller;


import com.loopers.ordering.order.application.dto.in.OrderCreateInDto;
import com.loopers.ordering.order.application.dto.out.OrderDetailOutDto;
import com.loopers.ordering.order.application.facade.OrderCommandFacade;
import com.loopers.ordering.order.interfaces.web.request.OrderCreateRequest;
import com.loopers.ordering.order.interfaces.web.response.OrderDetailResponse;
import com.loopers.user.user.support.common.HeaderValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderCommandController {

	// facade
	private final OrderCommandFacade orderCommandFacade;


	/**
	 * 주문 명령 컨트롤러 (사용자 인증 필요)
	 * 1. 주문 생성
	 */

	// 1. 주문 생성
	@PostMapping
	public ResponseEntity<OrderDetailResponse> createOrder(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@Valid @RequestBody OrderCreateRequest request
	) {

		// 인증 헤더 검증
		HeaderValidator.validate(loginId, password);

		// InDto 생성
		OrderCreateInDto inDto = new OrderCreateInDto(request.cartItemIds(), request.requestId(), request.couponId());

		// 주문 생성
		OrderDetailOutDto outDto = orderCommandFacade.createOrder(loginId, password, inDto);

		// 응답 변환
		OrderDetailResponse response = OrderDetailResponse.from(outDto);

		// 201 Created 반환
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

}
