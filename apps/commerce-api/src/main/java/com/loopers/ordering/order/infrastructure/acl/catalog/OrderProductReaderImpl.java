package com.loopers.ordering.order.infrastructure.acl.catalog;


import com.loopers.catalog.product.application.facade.ProductQueryFacade;
import com.loopers.catalog.product.domain.model.Product;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductInfo;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * ACL (Anti-Corruption Layer) - 주문 BC → 카탈로그 BC 통합 구현체 (상품 조회)
 * 주문 생성 시 상품 ID 목록에 해당하는 활성 상품 정보를 일괄 조회하는 역할 (Provider Facade에 위임)
 */
@Component
@RequiredArgsConstructor
public class OrderProductReaderImpl implements OrderProductReader {

	// facade: 상품 조회 파사드 (catalog BC)
	private final ProductQueryFacade productQueryFacade;


	/**
	 * 상품 ID 목록으로 상품 정보 일괄 조회
	 * 1. Provider Facade를 통해 활성 상품 조회 후 주문용 정보로 변환
	 */

	// 1. 상품 ID 목록으로 활성 상품 정보를 일괄 조회하여 주문용 정보로 변환
	@Override
	public List<OrderProductInfo> readProducts(List<Long> productIds) {

		// Provider Facade를 통해 활성 상품 일괄 조회
		List<Product> products = productQueryFacade.findActiveByIds(productIds);

		// Product 도메인 모델 → OrderProductInfo 변환 (VO에서 원시값 추출)
		return products.stream()
			.map(product -> new OrderProductInfo(
				product.getId(),
				product.getName().value(),
				product.getPrice().value(),
				product.getStock().value()
			))
			.toList();
	}

}
