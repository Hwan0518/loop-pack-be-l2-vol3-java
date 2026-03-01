package com.loopers.ordering.order.application.facade;


import com.loopers.ordering.order.application.dto.in.OrderCreateInDto;
import com.loopers.ordering.order.application.dto.out.OrderDetailOutDto;
import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemInfo;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductInfo;
import com.loopers.ordering.order.application.service.OrderCheckoutCommandService;
import com.loopers.ordering.order.application.service.OrderIdempotencyQueryService;
import com.loopers.ordering.order.application.service.OrderPlacementCommandService;
import com.loopers.ordering.order.application.service.OrderQueryService;
import com.loopers.ordering.order.domain.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;


@Service
@RequiredArgsConstructor
public class OrderCommandFacade {

	// service
	private final OrderIdempotencyQueryService orderIdempotencyQueryService;
	private final OrderCheckoutCommandService orderCheckoutCommandService;
	private final OrderPlacementCommandService orderPlacementCommandService;
	private final OrderQueryService orderQueryService;


	/**
	 * 주문 명령 파사드
	 * 1. 주문 생성 (인증 → 멱등성 검사 → 장바구니 조회 → 상품 조회 → 재고 차감 → 주문 생성)
	 */

	// 1. 주문 생성
	@Transactional
	public OrderDetailOutDto createOrder(String loginId, String password, OrderCreateInDto inDto) {

		// 1. 사용자 인증
		Long userId = orderCheckoutCommandService.authenticate(loginId, password);

		// 2. 멱등성 검사: 동일 requestId로 이미 주문이 존재하면 기존 주문 반환
		Optional<Long> existingOrderId = orderIdempotencyQueryService.findOrderIdByRequestId(userId, inDto.requestId());
		if (existingOrderId.isPresent()) {
			Order existingOrder = orderQueryService.findById(existingOrderId.get());
			return OrderDetailOutDto.from(existingOrder);
		}

		// 3. 장바구니 항목 조회
		List<OrderCartItemInfo> cartItems = orderCheckoutCommandService.readCartItemsByIds(userId, inDto.cartItemIds());

		// 4. 상품 정보 조회
		List<Long> productIds = cartItems.stream()
			.map(OrderCartItemInfo::productId)
			.toList();
		List<OrderProductInfo> products = orderCheckoutCommandService.readProducts(productIds);

		// 5. 재고 차감
		orderCheckoutCommandService.decreaseStocks(cartItems);

		// 6. 주문 생성
		List<Long> resolvedCartItemIds = cartItems.stream()
			.map(OrderCartItemInfo::cartItemId)
			.toList();
		Order order = orderPlacementCommandService.createOrder(userId, inDto.requestId(), cartItems, products, resolvedCartItemIds);

		// 7. 장바구니 항목 정리 (Cross-BC 부수효과)
		orderPlacementCommandService.deleteCartItems(userId, resolvedCartItemIds);

		// 8. DTO 변환
		return OrderDetailOutDto.from(order);
	}

}
