package com.loopers.ordering.order.application.port.out.client.catalog;


public interface OrderStockManager {

	/**
	 * 주문용 재고 관리 포트
	 * 1. 재고 차감
	 */

	// 1. 재고 차감
	void decreaseStock(Long productId, Long quantity);

}
