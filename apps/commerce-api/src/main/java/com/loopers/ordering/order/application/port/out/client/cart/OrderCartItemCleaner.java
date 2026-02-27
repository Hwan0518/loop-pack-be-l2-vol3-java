package com.loopers.ordering.order.application.port.out.client.cart;


import java.util.List;


public interface OrderCartItemCleaner {

	/**
	 * 주문용 장바구니 항목 정리 포트
	 * 1. 사용자 ID와 장바구니 항목 ID 목록으로 삭제
	 */

	// 1. 사용자 ID와 장바구니 항목 ID 목록으로 삭제
	void deleteCartItems(Long userId, List<Long> cartItemIds);

}
