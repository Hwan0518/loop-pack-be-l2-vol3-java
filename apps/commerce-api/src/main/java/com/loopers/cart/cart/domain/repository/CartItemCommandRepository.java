package com.loopers.cart.cart.domain.repository;


import com.loopers.cart.cart.domain.model.CartItem;

import java.util.List;


public interface CartItemCommandRepository {

	/**
	 * 장바구니 항목 명령 리포지토리
	 * 1. 장바구니 항목 저장
	 * 2. 장바구니 항목 삭제
	 * 3. 상품 ID로 장바구니 항목 전체 삭제
	 * 4. ID 목록으로 장바구니 항목 전체 삭제
	 * 5. 사용자 ID와 ID 목록으로 장바구니 항목 전체 삭제
	 */

	// 1. 장바구니 항목 저장
	CartItem save(CartItem cartItem);

	// 2. 장바구니 항목 삭제
	void delete(CartItem cartItem);

	// 3. 상품 ID로 장바구니 항목 전체 삭제
	void deleteAllByProductId(Long productId);

	// 4. ID 목록으로 장바구니 항목 전체 삭제
	void deleteAllByIds(List<Long> ids);

	// 5. 사용자 ID와 ID 목록으로 장바구니 항목 전체 삭제
	void deleteAllByUserIdAndIds(Long userId, List<Long> ids);

}
