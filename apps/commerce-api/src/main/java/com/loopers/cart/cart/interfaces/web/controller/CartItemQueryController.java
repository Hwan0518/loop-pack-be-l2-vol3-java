package com.loopers.cart.cart.interfaces.web.controller;


import com.loopers.cart.cart.application.dto.out.CartOutDto;
import com.loopers.cart.cart.application.dto.out.CartStatusOutDto;
import com.loopers.cart.cart.application.facade.CartItemQueryFacade;
import com.loopers.cart.cart.interfaces.web.response.CartResponse;
import com.loopers.cart.cart.interfaces.web.response.CartStatusResponse;
import com.loopers.user.user.support.common.HeaderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartItemQueryController {

	// facade
	private final CartItemQueryFacade cartItemQueryFacade;


	/**
	 * 장바구니 조회 컨트롤러 (사용자 인증 필요)
	 * 1. 장바구니 조회
	 * 2. 장바구니 상태 조회
	 */

	// 1. 장바구니 조회
	@GetMapping
	public ResponseEntity<CartResponse> getCart(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password
	) {

		// 인증 헤더 검증
		HeaderValidator.validate(loginId, password);

		// 장바구니 조회
		CartOutDto outDto = cartItemQueryFacade.getCart(loginId, password);

		// 응답 변환
		CartResponse response = CartResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}


	// 2. 장바구니 상태 조회
	@GetMapping("/status")
	public ResponseEntity<CartStatusResponse> getCartStatus(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password
	) {

		// 인증 헤더 검증
		HeaderValidator.validate(loginId, password);

		// 장바구니 상태 조회
		CartStatusOutDto outDto = cartItemQueryFacade.getCartStatus(loginId, password);

		// 응답 변환
		CartStatusResponse response = CartStatusResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}

}
