package com.loopers.cart.cart.application.service;


import com.loopers.cart.cart.application.dto.out.CartStatusItemOutDto;
import com.loopers.cart.cart.application.dto.out.CartStatusOutDto;
import com.loopers.cart.cart.domain.model.CartItem;
import com.loopers.cart.cart.domain.repository.CartItemQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
public class CartItemQueryService {

	// repository
	private final CartItemQueryRepository cartItemQueryRepository;


	/**
	 * 장바구니 항목 조회 서비스
	 * 1. 사용자의 장바구니 항목 목록 조회
	 * 2. 사용자의 장바구니 상태 조회
	 * 3. 사용자의 선택된 장바구니 항목 조회
	 * 4. 사용자 ID와 ID 목록으로 장바구니 항목 조회
	 */

	// 1. 사용자의 장바구니 항목 목록 조회
	@Transactional(readOnly = true)
	public List<CartItem> getCartByUserId(Long userId) {
		return cartItemQueryRepository.findByUserId(userId);
	}


	// 2. 사용자의 장바구니 상태 조회
	@Transactional(readOnly = true)
	public CartStatusOutDto getCartStatus(Long userId) {

		// 전체 장바구니 항목 조회
		List<CartItem> allItems = cartItemQueryRepository.findByUserId(userId);

		// 선택된 항목 수 계산
		int selectedCount = (int) allItems.stream().filter(CartItem::isSelected).count();

		// 상태 항목 DTO 변환
		List<CartStatusItemOutDto> itemDtos = allItems.stream()
			.map(CartStatusItemOutDto::from)
			.toList();

		return new CartStatusOutDto(allItems.size(), selectedCount, itemDtos);
	}


	// 3. 사용자의 선택된 장바구니 항목 조회
	@Transactional(readOnly = true)
	public List<CartItem> findSelectedByUserId(Long userId) {
		return cartItemQueryRepository.findSelectedByUserId(userId);
	}


	// 4. 사용자 ID와 ID 목록으로 장바구니 항목 조회
	@Transactional(readOnly = true)
	public List<CartItem> findByUserIdAndIds(Long userId, List<Long> ids) {
		return cartItemQueryRepository.findByUserIdAndIds(userId, ids);
	}

}
