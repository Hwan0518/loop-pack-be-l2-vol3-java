package com.loopers.ordering.order.application.service;


import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemInfo;
import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemReader;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductInfo;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductReader;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderStockManager;
import com.loopers.ordering.order.application.port.out.client.user.UserAuthenticator;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class OrderCheckoutCommandService {

	// port
	private final OrderCartItemReader orderCartItemReader;
	private final OrderProductReader orderProductReader;
	private final OrderStockManager orderStockManager;
	private final UserAuthenticator userAuthenticator;


	/**
	 * 주문 체크아웃 명령 서비스
	 * 1. 사용자 인증
	 * 2. 장바구니 항목 ID 목록으로 조회
	 * 3. 상품 정보 조회
	 * 4. 재고 차감
	 */

	// 1. 사용자 인증
	@Transactional(readOnly = true)
	public Long authenticate(String loginId, String password) {
		return userAuthenticator.authenticate(loginId, password);
	}

	// 2. 장바구니 항목 ID 목록으로 조회
	@Transactional(readOnly = true)
	public List<OrderCartItemInfo> readCartItemsByIds(Long userId, List<Long> cartItemIds) {

		// port: 장바구니 BC에서 특정 장바구니 항목 조회
		List<OrderCartItemInfo> cartItems = orderCartItemReader.readCartItemsByIds(userId, cartItemIds);

		// 장바구니 비어있음 검증
		if (cartItems.isEmpty()) {
			throw new CoreException(ErrorType.EMPTY_CART);
		}

		return cartItems;
	}


	// 3. 상품 정보 조회
	@Transactional(readOnly = true)
	public List<OrderProductInfo> readProducts(List<Long> productIds) {

		// port: 카탈로그 BC에서 상품 정보 조회
		return orderProductReader.readProducts(productIds);
	}


	// 4. 재고 차감 (productId 정렬 고정 + 중복 수량 합산 — 데드락 방지)
	@Transactional
	public void decreaseStocks(List<OrderCartItemInfo> cartItems) {

		// 중복 productId 수량 합산 후 오름차순 정렬 (락 획득 순서 고정 — 데드락 방지)
		Map<Long, Long> aggregated = cartItems.stream()
			.collect(Collectors.groupingBy(
				OrderCartItemInfo::productId,
				Collectors.summingLong(OrderCartItemInfo::quantity)
			));

		// productId 오름차순 정렬 후 재고 차감
		aggregated.entrySet().stream()
			.sorted(Comparator.comparingLong(Map.Entry::getKey))
			.forEach(entry -> orderStockManager.decreaseStock(entry.getKey(), entry.getValue()));
	}

}
