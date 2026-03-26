package com.loopers.payment.payment.infrastructure.acl.order;


import com.loopers.ordering.order.application.facade.OrderCommandFacade;
import com.loopers.ordering.order.application.facade.OrderQueryFacade;
import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.OrderItem;
import com.loopers.payment.payment.application.port.out.client.order.PaymentOrderInfo;
import com.loopers.payment.payment.application.port.out.client.order.PaymentOrderItemInfo;
import com.loopers.payment.payment.application.port.out.client.order.PaymentOrderReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * ACL (Anti-Corruption Layer) - Payment BC → Order BC (주문 조회)
 * 1. 결제 요청 시 주문 정보를 비관적 락으로 조회 (Provider Facade에 위임)
 * 2. 주문 항목 조회 (ORDER_PAID payload용)
 */
@Component
@RequiredArgsConstructor
public class PaymentOrderReaderImpl implements PaymentOrderReader {

	// facade
	private final OrderCommandFacade orderCommandFacade;
	private final OrderQueryFacade orderQueryFacade;


	// 1. 결제용 주문 조회 — Provider Facade에 위임
	@Override
	public PaymentOrderInfo findOrderForPayment(Long orderId, Long userId) {

		// Order BC Facade 호출 (비관적 락 + 사용자 검증)
		Order order = orderCommandFacade.findOrderForPayment(orderId, userId);

		// Payment BC VO로 변환 (OrderStatus enum → String 변환하여 BC 경계 분리)
		return new PaymentOrderInfo(
			order.getId(),
			order.getUserId(),
			order.getTotalPrice(),
			order.getStatus().name()
		);
	}


	// 2. 주문 항목 조회 — Provider QueryFacade에 위임
	@Override
	public List<PaymentOrderItemInfo> findOrderItems(Long orderId) {

		// Order BC QueryFacade 호출
		List<OrderItem> items = orderQueryFacade.findOrderItems(orderId);

		// Payment BC VO로 변환
		return items.stream()
			.map(item -> new PaymentOrderItemInfo(item.getProductId(), item.getQuantity()))
			.toList();
	}

}
