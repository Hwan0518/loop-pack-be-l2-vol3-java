package com.loopers.cart.cart.application.facade;


import com.loopers.cart.cart.application.dto.in.CartItemAddInDto;
import com.loopers.cart.cart.application.dto.in.CartItemSelectionInDto;
import com.loopers.cart.cart.application.dto.in.CartItemUpdateQuantityInDto;
import com.loopers.cart.cart.application.dto.out.CartItemOutDto;
import com.loopers.cart.cart.application.dto.out.CartItemSelectionOutDto;
import com.loopers.cart.cart.application.service.CartItemCommandService;
import com.loopers.cart.cart.domain.model.CartItem;
import java.util.List;
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
	 * 5. 상품 ID로 장바구니 항목 전체 삭제 (Cross-BC 전용 — ACL에서 호출)
	 * 6. 사용자 ID와 ID 목록으로 장바구니 항목 전체 삭제 (Cross-BC 전용 — ACL에서 호출)
	 */

	// 1. 장바구니 항목 추가
	@Transactional
	public CartItemOutDto addItem(Long userId, CartItemAddInDto inDto) {

		// 상품 존재 여부 검증 + 장바구니 항목 추가
		CartItem cartItem = cartItemCommandService.addItem(userId, inDto);

		// DTO 변환
		return CartItemOutDto.from(cartItem);
	}


	// 2. 장바구니 항목 수량 변경
	@Transactional
	public CartItemOutDto updateQuantity(Long userId, Long cartItemId, CartItemUpdateQuantityInDto inDto) {

		// 수량 변경
		CartItem cartItem = cartItemCommandService.updateQuantity(cartItemId, userId, inDto);

		// DTO 변환
		return CartItemOutDto.from(cartItem);
	}


	// 3. 장바구니 항목 삭제
	@Transactional
	public void deleteItem(Long userId, Long cartItemId) {

		// 장바구니 항목 삭제
		cartItemCommandService.deleteItem(cartItemId, userId);
	}


	// 4. 장바구니 항목 선택 상태 변경
	@Transactional
	public CartItemSelectionOutDto updateSelection(Long userId, CartItemSelectionInDto inDto) {

		return cartItemCommandService.updateSelection(userId, inDto);
	}


	// 5. 상품 ID로 장바구니 항목 전체 삭제 (Cross-BC 전용 — ACL에서 호출)
	@Transactional
	public void deleteAllByProductId(Long productId) {
		cartItemCommandService.deleteAllByProductId(productId);
	}


	// 6. 사용자 ID와 ID 목록으로 장바구니 항목 전체 삭제 (Cross-BC 전용 — ACL에서 호출)
	@Transactional
	public void deleteAllByUserIdAndIds(Long userId, List<Long> ids) {
		cartItemCommandService.deleteAllByUserIdAndIds(userId, ids);
	}

}
