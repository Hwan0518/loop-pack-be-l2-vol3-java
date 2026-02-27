package com.loopers.cart.cart.application.facade;


import com.loopers.cart.cart.application.dto.in.CartItemAddInDto;
import com.loopers.cart.cart.application.dto.in.CartItemSelectionInDto;
import com.loopers.cart.cart.application.dto.in.CartItemUpdateQuantityInDto;
import com.loopers.cart.cart.application.dto.out.CartItemOutDto;
import com.loopers.cart.cart.application.dto.out.CartItemSelectionOutDto;
import com.loopers.cart.cart.application.service.CartItemCommandService;
import com.loopers.cart.cart.domain.model.CartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class CartItemCommandFacade {

	// service
	private final CartItemCommandService cartItemCommandService;


	/**
	 * 장바구니 항목 명령 파사드
	 * 1. 장바구니 항목 추가
	 * 2. 장바구니 항목 수량 변경
	 * 3. 장바구니 항목 삭제
	 * 4. 장바구니 항목 선택 상태 변경
	 */

	// 1. 장바구니 항목 추가
	@Transactional
	public CartItemOutDto addItem(String loginId, String password, CartItemAddInDto inDto) {

		// 사용자 인증
		Long userId = cartItemCommandService.authenticate(loginId, password);

		// 상품 존재 여부 검증 + 장바구니 항목 추가
		CartItem cartItem = cartItemCommandService.addItem(userId, inDto);

		// DTO 변환
		return CartItemOutDto.from(cartItem);
	}


	// 2. 장바구니 항목 수량 변경
	@Transactional
	public CartItemOutDto updateQuantity(String loginId, String password, Long cartItemId, CartItemUpdateQuantityInDto inDto) {

		// 사용자 인증
		Long userId = cartItemCommandService.authenticate(loginId, password);

		// 수량 변경
		CartItem cartItem = cartItemCommandService.updateQuantity(cartItemId, userId, inDto);

		// DTO 변환
		return CartItemOutDto.from(cartItem);
	}


	// 3. 장바구니 항목 삭제
	@Transactional
	public void deleteItem(String loginId, String password, Long cartItemId) {

		// 사용자 인증
		Long userId = cartItemCommandService.authenticate(loginId, password);

		// 장바구니 항목 삭제
		cartItemCommandService.deleteItem(cartItemId, userId);
	}


	// 4. 장바구니 항목 선택 상태 변경
	@Transactional
	public CartItemSelectionOutDto updateSelection(String loginId, String password, CartItemSelectionInDto inDto) {

		// 사용자 인증
		Long userId = cartItemCommandService.authenticate(loginId, password);

		return cartItemCommandService.updateSelection(userId, inDto);
	}

}
