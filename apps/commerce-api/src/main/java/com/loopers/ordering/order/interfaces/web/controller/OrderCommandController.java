package com.loopers.ordering.order.interfaces.web.controller;


import com.loopers.ordering.order.application.dto.in.OrderCreateInDto;
import com.loopers.ordering.order.application.dto.out.OrderDetailOutDto;
import com.loopers.ordering.order.application.facade.OrderCommandFacade;
import com.loopers.ordering.order.interfaces.web.request.OrderCreateRequest;
import com.loopers.ordering.order.interfaces.web.response.OrderDetailResponse;
import com.loopers.support.common.auth.AuthenticationResolver;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
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
	// auth
	private final AuthenticationResolver authenticationResolver;


	/**
	 * 주문 명령 컨트롤러 (사용자 인증 필요)
	 * 1. 주문 생성
	 */

	// 1. 주문 생성 (입장 토큰 검증 포함)
	@PostMapping
	public ResponseEntity<OrderDetailResponse> createOrder(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@RequestHeader(value = "X-Entry-Token", required = false) String entryToken,
		@Valid @RequestBody OrderCreateRequest request
	) {

		// 입장 토큰 헤더 검증 (required=false → 일관된 에러 응답을 위해 수동 검증)
		if (entryToken == null || entryToken.isBlank()) {
			throw new CoreException(ErrorType.INVALID_QUEUE_TOKEN);
		}

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// InDto 생성
		OrderCreateInDto inDto = new OrderCreateInDto(request.cartItemIds(), request.requestId(), request.couponId());

		// 주문 생성 (입장 토큰 검증 + 주문 완료 후 토큰 삭제 포함)
		OrderDetailOutDto outDto = orderCommandFacade.createOrder(userId, inDto, entryToken);

		// 응답 변환
		OrderDetailResponse response = OrderDetailResponse.from(outDto);

		// 201 Created 반환
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

}
