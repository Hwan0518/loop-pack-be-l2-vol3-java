package com.loopers.ordering.order.application.port.out.client.cart;


import java.util.List;


public interface OrderCartItemReader {

	/**
	 * 주문용 장바구니 항목 조회 포트
	 * 1. 사용자의 선택된 장바구니 항목 조회
	 * 2. 사용자의 특정 장바구니 항목 ID 목록으로 조회
	 */

	// 1. 사용자의 선택된 장바구니 항목 조회
	List<OrderCartItemInfo> readSelectedCartItems(Long userId);

	// 2. 사용자의 특정 장바구니 항목 ID 목록으로 조회
	List<OrderCartItemInfo> readCartItemsByIds(Long userId, List<Long> cartItemIds);

}
