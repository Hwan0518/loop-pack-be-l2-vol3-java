package com.loopers.ordering.order.application.service;


import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemInfo;
import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemReader;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductInfo;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductReader;
import com.loopers.ordering.order.application.port.out.client.queue.OrderEntryTokenValidator;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
public class OrderCheckoutCommandService {

	// port
	private final OrderCartItemReader orderCartItemReader;
	private final OrderProductReader orderProductReader;
	private final OrderEntryTokenValidator orderEntryTokenValidator;


	/**
	 * 주문 체크아웃 조회 서비스
	 * 1. 장바구니 항목 ID 목록으로 조회
	 * 2. 상품 정보 조회
	 * 3. 입장 토큰 검증 + 주문 처리 락 획득 (동시 주문 방지)
	 * 4. 주문 완료 후 정리 (토큰 삭제 + 락 해제)
	 * 5. 주문 실패 후 정리 (락만 해제)
	 */

	// 1. 장바구니 항목 ID 목록으로 조회
	@Transactional(readOnly = true)
	public List<OrderCartItemInfo> readCartItemsByIds(Long userId, List<Long> cartItemIds) {

		// port: 장바구니 BC에서 특정 장바구니 항목 조회
		List<OrderCartItemInfo> cartItems = orderCartItemReader.readCartItemsByIds(userId, cartItemIds);

		// 장바구니 비어있음 검증
		if (cartItems.isEmpty()) {
			throw new CoreException(ErrorType.EMPTY_CART);
		}

		// 요청한 고유 항목 수와 조회 결과 수 불일치 검증 (타인 소유/삭제 항목 포함 시 — All-or-Nothing)
		long distinctRequestCount = cartItemIds.stream().distinct().count();
		if (cartItems.size() != distinctRequestCount) {
			throw new CoreException(ErrorType.CART_ITEM_NOT_FOUND);
		}

		return cartItems;
	}


	// 2. 상품 정보 조회
	@Transactional(readOnly = true)
	public List<OrderProductInfo> readProducts(List<Long> productIds) {

		// 중복 제거 (장바구니 항목에서 동일 상품 중복 가능)
		List<Long> distinctIds = productIds.stream().distinct().toList();

		// port: 카탈로그 BC에서 상품 정보 일괄 조회
		List<OrderProductInfo> products = orderProductReader.readProducts(distinctIds);

		// 요청한 고유 상품 수와 조회 결과 수 불일치 검증 (삭제/미존재 상품 포함 시)
		if (products.size() != distinctIds.size()) {
			throw new CoreException(ErrorType.PRODUCT_NOT_FOUND);
		}

		return products;
	}


	// 3. 입장 토큰 검증 + 주문 처리 락 획득 (동시 주문 방지)
	public void validateAndLockEntryToken(Long userId, String entryToken) {
		orderEntryTokenValidator.validateAndLock(userId, entryToken);
	}


	// 4. 주문 완료 후 정리 (토큰 삭제 + 락 해제)
	public void completeEntryToken(Long userId) {
		orderEntryTokenValidator.completeOrder(userId);
	}


	// 5. 주문 실패 후 정리 (락만 해제 — 토큰 보존)
	public void releaseOrderLock(Long userId) {
		orderEntryTokenValidator.releaseOrderLock(userId);
	}

}
