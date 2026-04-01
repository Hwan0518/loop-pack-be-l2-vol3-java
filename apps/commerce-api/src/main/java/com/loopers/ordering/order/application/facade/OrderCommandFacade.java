package com.loopers.ordering.order.application.facade;


import com.loopers.ordering.order.application.dto.in.OrderCreateInDto;
import com.loopers.ordering.order.application.dto.out.OrderDetailOutDto;
import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemInfo;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductInfo;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderStockManager;
import com.loopers.ordering.order.application.port.out.client.coupon.OrderCouponRestorer;
import com.loopers.ordering.order.application.service.OrderCheckoutCommandService;
import com.loopers.ordering.order.application.service.OrderQueryService;
import com.loopers.ordering.order.application.service.OrderCommandService;
import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.OrderItem;
import com.loopers.ordering.order.domain.model.enums.OrderStatus;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	 * 2. 결제용 주문 조회 (Cross-BC 전용 — ACL에서 호출, 비관적 락 + 사용자 검증)
	 * 3. 주문 상태 변경 — Order 객체 직접 전달 (내부 스케줄러에서 호출)
	 * 4. 주문 상태 변경 — orderId로 조회 후 변경 (Cross-BC 전용 — ACL에서 호출)
	 */

	// 1. 주문 생성 (입장 토큰 검증 포함)
	public OrderDetailOutDto createOrder(Long userId, OrderCreateInDto inDto, String entryToken) {

		// Phase 1: 읽기 (NO TX — 각 Service가 자체 readOnly TX 보유)

		// 멱등성 검사 (토큰 검증보다 우선 — 주문 성공 후 토큰 삭제되므로 재시도 시 멱등 반환 필요)
		Optional<Order> existingOrder = orderQueryService.findByUserIdAndRequestId(userId, inDto.requestId());
		if (existingOrder.isPresent()) {
			return OrderDetailOutDto.from(existingOrder.get());
		}

		// 입장 토큰 원자적 소비 (검증 + 삭제 — 동시 주문 방지)
		orderCheckoutCommandService.consumeEntryToken(userId, entryToken);

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
		OrderDetailOutDto result;
		try {
			result = orderCommandService.createOrder(userId, inDto, cartItems, products, resolvedCartItemIds);
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

		return result;
	}


	// 2. 결제용 주문 조회 (Cross-BC 전용 — ACL에서 호출, 비관적 락 + 사용자 검증)
	public Order findOrderForPayment(Long orderId, Long userId) {

		// 비관적 쓰기 락으로 주문 조회 (같은 BC 내 QueryService 호출)
		Order order = orderQueryService.findByIdForUpdate(orderId);

		// 본인 주문 검증
		if (!order.getUserId().equals(userId)) {
			throw new CoreException(ErrorType.ORDER_NOT_FOUND);
		}

		return order;
	}


	// 3. 주문 상태 변경 — Order 객체 직접 전달 (내부 스케줄러에서 호출)
	public void changeOrderStatus(Order order, OrderStatus status) {
		orderCommandService.changeOrderStatus(order, status);
	}


	// 4. 주문 상태 변경 — orderId로 조회 후 변경 (Cross-BC 전용 — ACL에서 호출)
	public void changeOrderStatusById(Long orderId, OrderStatus status) {

		// 주문 조회 (같은 BC 내 QueryService 호출)
		Order order = orderQueryService.findById(orderId);

		// 주문 상태 변경
		orderCommandService.changeOrderStatus(order, status);
	}


	/**
	 * private method
	 * - MySQL 유니크 제약 위반 여부 판별
	 */

	// MySQL "Duplicate entry" 메시지로 유니크 제약 위반 여부 판별
	private boolean isDuplicateKeyViolation(DataIntegrityViolationException e) {
		String rootMessage = e.getMostSpecificCause().getMessage();
		return rootMessage != null && rootMessage.contains("Duplicate entry");
	}

}
