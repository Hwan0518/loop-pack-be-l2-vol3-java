package com.loopers.cart.cart.application.port.out.client.catalog;


public interface CartProductReader {

	/**
	 * 장바구니 상품 조회 포트 (Cross-BC: Cart -> Catalog)
	 * 1. 상품 존재 여부 검증
	 */

	// 1. 상품 존재 여부 검증 (미존재 시 Provider 예외 전파, 에러 매핑은 호출측 Service 책임)
	void validateProductExists(Long productId);

}
