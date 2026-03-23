package com.loopers.ordering.order.infrastructure.acl.payment;


import com.loopers.ordering.order.application.port.out.client.payment.OrderPaymentReader;
import com.loopers.payment.payment.application.facade.PaymentQueryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * ACL (Anti-Corruption Layer) - Ordering BC → Payment BC (결제 조회)
 * 주문 만료 시 진행 중인 결제 존재 여부를 확인하는 역할 (Provider Facade에 위임)
 */
@Component
@RequiredArgsConstructor
public class OrderPaymentReaderImpl implements OrderPaymentReader {

	// facade: 결제 조회 파사드 (payment BC)
	private final PaymentQueryFacade paymentQueryFacade;


	/**
	 * 결제 존재 여부 확인
	 * 1. Provider Facade에 위임
	 */

	// 1. REQUESTED 결제 존재 여부 확인 — Provider Facade에 위임
	@Override
	public boolean existsRequestedPayment(Long orderId) {
		return paymentQueryFacade.existsRequestedByOrderId(orderId);
	}

}
