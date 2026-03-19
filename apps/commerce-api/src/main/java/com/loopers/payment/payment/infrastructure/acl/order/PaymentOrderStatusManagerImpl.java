package com.loopers.payment.payment.infrastructure.acl.order;


import com.loopers.ordering.order.application.facade.OrderCommandFacade;
import com.loopers.ordering.order.domain.model.enums.OrderStatus;
import com.loopers.payment.payment.application.port.out.client.order.PaymentOrderStatusManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * ACL (Anti-Corruption Layer) - Payment BC → Order BC (주문 상태 관리)
 * 결제 결과에 따라 주문 상태를 변경하는 역할 (Provider Facade에 위임)
 */
@Component
@RequiredArgsConstructor
public class PaymentOrderStatusManagerImpl implements PaymentOrderStatusManager {

	// facade: 주문 명령 파사드 (ordering BC)
	private final OrderCommandFacade orderCommandFacade;


	/**
	 * 주문 상태 관리 — Provider Facade에 위임
	 * 1. 주문 상태를 PAID로 변경
	 * 2. 주문 상태를 PAYMENT_FAILED로 변경
	 * 3. 주문 상태를 PENDING_PAYMENT로 변경 (재결제 시)
	 */

	// 1. 주문 상태를 PAID로 변경 — Provider Facade에 위임
	@Override
	public void markOrderPaid(Long orderId) {
		orderCommandFacade.changeOrderStatusById(orderId, OrderStatus.PAID);
	}


	// 2. 주문 상태를 PAYMENT_FAILED로 변경 — Provider Facade에 위임
	@Override
	public void markOrderPaymentFailed(Long orderId) {
		orderCommandFacade.changeOrderStatusById(orderId, OrderStatus.PAYMENT_FAILED);
	}


	// 3. 주문 상태를 PENDING_PAYMENT로 변경 — Provider Facade에 위임 (재결제 시)
	@Override
	public void markOrderPendingPayment(Long orderId) {
		orderCommandFacade.changeOrderStatusById(orderId, OrderStatus.PENDING_PAYMENT);
	}

}
