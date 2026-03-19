package com.loopers.payment.payment.application.facade;


import com.loopers.payment.payment.application.service.PaymentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class PaymentQueryFacade {

	// service
	private final PaymentQueryService paymentQueryService;


	/**
	 * 결제 조회 파사드
	 * 1. 주문 ID로 REQUESTED 결제 존재 여부 확인 (Cross-BC 전용 — ACL에서 호출)
	 */

	// 1. 주문 ID로 REQUESTED 결제 존재 여부 확인 (Cross-BC 전용 — ACL에서 호출)
	@Transactional(readOnly = true)
	public boolean existsRequestedByOrderId(Long orderId) {
		return paymentQueryService.existsRequestedByOrderId(orderId);
	}

}
