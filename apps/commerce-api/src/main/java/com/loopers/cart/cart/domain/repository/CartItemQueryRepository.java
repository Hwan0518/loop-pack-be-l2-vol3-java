package com.loopers.cart.cart.domain.repository;


import com.loopers.cart.cart.domain.model.CartItem;

import java.util.List;
import java.util.Optional;


public interface CartItemQueryRepository {

	/**
	 * 장바구니 항목 조회 리포지토리
	 * 1. ID로 장바구니 항목 조회
	 * 2. 사용자 ID로 장바구니 항목 목록 조회
	 * 3. 사용자 ID와 상품 ID로 장바구니 항목 조회
	 * 4. 사용자 ID로 선택된 장바구니 항목 목록 조회
	 * 5. 사용자 ID와 ID 목록으로 장바구니 항목 조회
	 */

	// 1. ID로 장바구니 항목 조회
	Optional<CartItem> findById(Long id);

	// 2. 사용자 ID로 장바구니 항목 목록 조회
	List<CartItem> findByUserId(Long userId);

	// 3. 사용자 ID와 상품 ID로 장바구니 항목 조회
	Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

	// 4. 사용자 ID로 선택된 장바구니 항목 목록 조회
	List<CartItem> findSelectedByUserId(Long userId);

	// 5. 사용자 ID와 ID 목록으로 장바구니 항목 조회
	List<CartItem> findByUserIdAndIds(Long userId, List<Long> ids);

}
