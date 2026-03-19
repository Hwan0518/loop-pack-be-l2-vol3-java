package com.loopers.payment.payment.infrastructure.acl.order;


import com.loopers.ordering.order.application.facade.OrderCommandFacade;
import com.loopers.ordering.order.domain.model.Order;
import com.loopers.payment.payment.application.port.out.client.order.PaymentOrderInfo;
import com.loopers.payment.payment.application.port.out.client.order.PaymentOrderReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * ACL (Anti-Corruption Layer) - Payment BC → Order BC (주문 조회)
 * 결제 요청 시 주문 정보를 비관적 락으로 조회하는 역할 (Provider Facade에 위임)
 */
@Component
@RequiredArgsConstructor
public class PaymentOrderReaderImpl implements PaymentOrderReader {

	// facade: 주문 명령 파사드 (ordering BC)
	private final OrderCommandFacade orderCommandFacade;


	/**
	 * 주문 조회
	 * 1. Provider Facade에 위임 (비관적 락 + 사용자 검증)
	 */

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

}
