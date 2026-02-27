package com.loopers.cart.cart.application.facade;


import com.loopers.cart.cart.application.dto.out.CartOutDto;
import com.loopers.cart.cart.application.dto.out.CartStatusOutDto;
import com.loopers.cart.cart.application.service.CartItemCommandService;
import com.loopers.cart.cart.application.service.CartItemQueryService;
import com.loopers.cart.cart.domain.model.CartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
public class CartItemQueryFacade {

	// service
	private final CartItemCommandService cartItemCommandService;
	private final CartItemQueryService cartItemQueryService;


	/**
	 * 장바구니 항목 조회 파사드
	 * 1. 장바구니 조회
	 * 2. 장바구니 상태 조회
	 * 3. 선택된 장바구니 항목 조회 (Cross-BC 전용 — ACL에서 호출)
	 * 4. ID 목록으로 장바구니 항목 조회 (Cross-BC 전용 — ACL에서 호출)
	 */

	// 1. 장바구니 조회
	@Transactional(readOnly = true)
	public CartOutDto getCart(String loginId, String password) {

		// 사용자 인증
		Long userId = cartItemCommandService.authenticate(loginId, password);

		// 장바구니 항목 목록 조회
		List<CartItem> cartItems = cartItemQueryService.getCartByUserId(userId);

		// DTO 변환
		return CartOutDto.from(cartItems);
	}


	// 2. 장바구니 상태 조회
	@Transactional(readOnly = true)
	public CartStatusOutDto getCartStatus(String loginId, String password) {

		// 사용자 인증
		Long userId = cartItemCommandService.authenticate(loginId, password);

		return cartItemQueryService.getCartStatus(userId);
	}


	// 3. 선택된 장바구니 항목 조회 (Cross-BC 전용 — ACL에서 호출)
	@Transactional(readOnly = true)
	public List<CartItem> findSelectedByUserId(Long userId) {
		return cartItemQueryService.findSelectedByUserId(userId);
	}


	// 4. ID 목록으로 장바구니 항목 조회 (Cross-BC 전용 — ACL에서 호출)
	@Transactional(readOnly = true)
	public List<CartItem> findByUserIdAndIds(Long userId, List<Long> ids) {
		return cartItemQueryService.findByUserIdAndIds(userId, ids);
	}

}
