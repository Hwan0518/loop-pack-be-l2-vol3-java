package com.loopers.ordering.order.application.service;


import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemInfo;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductInfo;
import com.loopers.ordering.order.domain.event.OrderCreatedEvent;
import com.loopers.ordering.order.domain.model.IdempotencyKey;
import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.OrderItem;
import com.loopers.ordering.order.domain.repository.IdempotencyKeyCommandRepository;
import com.loopers.ordering.order.domain.repository.OrderCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class OrderPlacementCommandService {

	// repository
	private final OrderCommandRepository orderCommandRepository;
	private final IdempotencyKeyCommandRepository idempotencyKeyCommandRepository;
	// event
	private final ApplicationEventPublisher eventPublisher;


	/**
	 * 주문 생성 명령 서비스
	 * 1. 주문 생성 (장바구니 항목 + 상품 정보 → 주문 + 멱등성 키 저장 + 이벤트 발행)
	 */

	// 1. 주문 생성
	@Transactional
	public Order createOrder(Long userId, String requestId,
		List<OrderCartItemInfo> cartItems, List<OrderProductInfo> products,
		List<Long> resolvedCartItemIds) {

		// 상품 정보를 productId 기준으로 맵핑
		Map<Long, OrderProductInfo> productMap = products.stream()
			.collect(Collectors.toMap(OrderProductInfo::productId, p -> p));

		// 장바구니 항목으로부터 주문 항목 생성
		List<OrderItem> orderItems = cartItems.stream()
			.map(cartItem -> {
				OrderProductInfo product = productMap.get(cartItem.productId());
				return OrderItem.create(
					cartItem.productId(),
					product.name(),
					product.price(),
					cartItem.quantity()
				);
			})
			.toList();

		// 총액 계산
		BigDecimal totalPrice = orderItems.stream()
			.map(OrderItem::getSubtotal)
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		// 주문 생성
		Order order = Order.create(userId, totalPrice, orderItems);

		// 주문 저장
		Order savedOrder = orderCommandRepository.save(order);

		// 멱등성 키 저장
		IdempotencyKey idempotencyKey = IdempotencyKey.create(userId, requestId, savedOrder.getId());
		idempotencyKeyCommandRepository.save(idempotencyKey);

		// 주문 생성 이벤트 발행 (장바구니 정리용)
		eventPublisher.publishEvent(new OrderCreatedEvent(
			savedOrder.getId(), userId, resolvedCartItemIds
		));

		return savedOrder;
	}

}
