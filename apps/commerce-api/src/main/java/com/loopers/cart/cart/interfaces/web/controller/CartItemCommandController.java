package com.loopers.cart.cart.interfaces.web.controller;


import com.loopers.cart.cart.application.dto.in.CartItemAddInDto;
import com.loopers.cart.cart.application.dto.in.CartItemSelectionInDto;
import com.loopers.cart.cart.application.dto.in.CartItemUpdateQuantityInDto;
import com.loopers.cart.cart.application.dto.out.CartItemOutDto;
import com.loopers.cart.cart.application.dto.out.CartItemSelectionOutDto;
import com.loopers.cart.cart.application.facade.CartItemCommandFacade;
import com.loopers.cart.cart.interfaces.web.request.CartItemAddRequest;
import com.loopers.cart.cart.interfaces.web.request.CartItemSelectionRequest;
import com.loopers.cart.cart.interfaces.web.request.CartItemUpdateQuantityRequest;
import com.loopers.cart.cart.interfaces.web.response.CartItemResponse;
import com.loopers.cart.cart.interfaces.web.response.CartItemSelectionResponse;
import com.loopers.support.common.auth.AuthenticationResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/cart/items")
@RequiredArgsConstructor
public class CartItemCommandController {

	// facade
	private final CartItemCommandFacade cartItemCommandFacade;
	// auth
	private final AuthenticationResolver authenticationResolver;


	/**
	 * 장바구니 항목 명령 컨트롤러 (사용자 인증 필요)
	 * 1. 장바구니 항목 추가
	 * 2. 장바구니 항목 수량 변경
	 * 3. 장바구니 항목 삭제
	 * 4. 장바구니 항목 선택 상태 변경
	 */

	// 1. 장바구니 항목 추가
	@PostMapping
	public ResponseEntity<CartItemResponse> addItem(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@Valid @RequestBody CartItemAddRequest request
	) {

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// InDto 생성
		CartItemAddInDto inDto = new CartItemAddInDto(request.productId(), request.quantity());

		// 장바구니 항목 추가
		CartItemOutDto outDto = cartItemCommandFacade.addItem(userId, inDto);

		// 응답 변환
		CartItemResponse response = CartItemResponse.from(outDto);

		// 201 Created 반환
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}


	// 2. 장바구니 항목 수량 변경
	@PutMapping("/{cartItemId}")
	public ResponseEntity<CartItemResponse> updateQuantity(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@PathVariable Long cartItemId,
		@Valid @RequestBody CartItemUpdateQuantityRequest request
	) {

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// InDto 생성
		CartItemUpdateQuantityInDto inDto = new CartItemUpdateQuantityInDto(request.quantity());

		// 수량 변경
		CartItemOutDto outDto = cartItemCommandFacade.updateQuantity(userId, cartItemId, inDto);

		// 응답 변환
		CartItemResponse response = CartItemResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}


	// 3. 장바구니 항목 삭제
	@DeleteMapping("/{cartItemId}")
	public ResponseEntity<Void> deleteItem(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@PathVariable Long cartItemId
	) {

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// 장바구니 항목 삭제
		cartItemCommandFacade.deleteItem(userId, cartItemId);

		// 204 No Content 반환
		return ResponseEntity.noContent().build();
	}


	// 4. 장바구니 항목 선택 상태 변경
	@PutMapping("/selection")
	public ResponseEntity<CartItemSelectionResponse> updateSelection(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@Valid @RequestBody CartItemSelectionRequest request
	) {

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// InDto 생성
		CartItemSelectionInDto inDto = new CartItemSelectionInDto(request.selectedCartItemIds());

		// 선택 상태 변경
		CartItemSelectionOutDto outDto = cartItemCommandFacade.updateSelection(userId, inDto);

		// 응답 변환
		CartItemSelectionResponse response = CartItemSelectionResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}

}
