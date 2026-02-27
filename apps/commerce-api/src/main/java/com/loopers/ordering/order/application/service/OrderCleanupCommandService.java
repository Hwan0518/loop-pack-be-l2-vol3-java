package com.loopers.ordering.order.application.service;


import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemCleaner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * 주문 부수효과 정리 서비스
 * 주문 생성 후 관련 Cross-BC 데이터 정리를 담당한다.
 */
@Service
@RequiredArgsConstructor
public class OrderCleanupCommandService {

	// port
	private final OrderCartItemCleaner orderCartItemCleaner;


	/**
	 * 주문 부수효과 정리
	 * 1. 장바구니 항목 삭제
	 */

	// 1. 장바구니 항목 삭제
	@Transactional
	public void deleteCartItems(Long userId, List<Long> cartItemIds) {
		orderCartItemCleaner.deleteCartItems(userId, cartItemIds);
	}

}
