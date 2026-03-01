package com.loopers.ordering.order.application.port.out.client.catalog;


import java.util.List;


public interface OrderProductReader {

	/**
	 * 주문용 상품 정보 조회 포트
	 * 1. 상품 ID 목록으로 상품 정보 일괄 조회
	 */

	// 1. 상품 ID 목록으로 상품 정보 일괄 조회
	List<OrderProductInfo> readProducts(List<Long> productIds);

}
