package com.loopers.ordering.order.application.facade;


import com.loopers.ordering.order.application.dto.in.OrderCreateInDto;
import com.loopers.ordering.order.application.dto.out.OrderDetailOutDto;
import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemInfo;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductInfo;
import com.loopers.ordering.order.application.service.OrderCheckoutCommandService;
import com.loopers.ordering.order.application.service.OrderQueryService;
import com.loopers.ordering.order.application.service.OrderCommandService;
import com.loopers.ordering.order.domain.model.Order;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;


@Service
@RequiredArgsConstructor
public class OrderCommandFacade {

	// service
	private final OrderCheckoutCommandService orderCheckoutCommandService;
	private final OrderQueryService orderQueryService;
	private final OrderCommandService orderCommandService;


	/**
	 * 주문 명령 파사드
	 * 1. 주문 생성 (읽기: NO TX — 쓰기: OrderCommandService 짧은 TX)
	 */

	// 1. 주문 생성
	public OrderDetailOutDto createOrder(Long userId, OrderCreateInDto inDto) {

		// Phase 1: 읽기 (NO TX — 각 Service가 자체 readOnly TX 보유)

		// 멱등성 검사: 동일 userId + requestId로 이미 주문이 존재하면 기존 주문 반환
		Optional<Order> existingOrder = orderQueryService.findByUserIdAndRequestId(userId, inDto.requestId());
		if (existingOrder.isPresent()) {
			return OrderDetailOutDto.from(existingOrder.get());
		}

		// 장바구니 항목 조회
		List<OrderCartItemInfo> cartItems = orderCheckoutCommandService.readCartItemsByIds(userId, inDto.cartItemIds());

		// 상품 정보 조회
		List<Long> productIds = cartItems.stream()
			.map(OrderCartItemInfo::productId)
			.toList();
		List<OrderProductInfo> products = orderCheckoutCommandService.readProducts(productIds);

		List<Long> resolvedCartItemIds = cartItems.stream()
			.map(OrderCartItemInfo::cartItemId)
			.toList();

		// Phase 2: 쓰기 (OrderCommandService — 짧은 @Transactional)
		try {
			return orderCommandService.createOrder(userId, inDto, cartItems, products, resolvedCartItemIds);
		} catch (DataIntegrityViolationException e) {
			// 유니크 제약 위반(user_id + request_id) race인 경우에만 멱등 처리
			if (isDuplicateKeyViolation(e)) {
				Order racedOrder = orderQueryService.findByUserIdAndRequestId(userId, inDto.requestId())
					.orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));
				return OrderDetailOutDto.from(racedOrder);
			}
			// 그 외 무결성 오류(FK, NOT NULL 등)는 그대로 전파
			throw e;
		}
	}


	// MySQL "Duplicate entry" 메시지로 유니크 제약 위반 여부 판별
	private boolean isDuplicateKeyViolation(DataIntegrityViolationException e) {
		String rootMessage = e.getMostSpecificCause().getMessage();
		return rootMessage != null && rootMessage.contains("Duplicate entry");
	}

}
