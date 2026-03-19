package com.loopers.ordering.order.application.port.out.client.catalog;


public interface OrderStockManager {

	/**
	 * 주문용 재고 관리 포트
	 * 1. 재고 차감
	 * 2. 재고 복원 (보상 트랜잭션 — 주문 만료 시 재고 증가)
	 */

	// 1. 재고 차감
	void decreaseStock(Long productId, Long quantity);

	// 2. 재고 복원 (보상 트랜잭션 — 주문 만료 시 재고 증가)
	void restoreStock(Long productId, Long quantity);

}
