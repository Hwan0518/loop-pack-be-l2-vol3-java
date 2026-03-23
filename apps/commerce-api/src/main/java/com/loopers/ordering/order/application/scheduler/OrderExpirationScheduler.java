package com.loopers.ordering.order.application.scheduler;


import com.loopers.ordering.order.application.port.out.client.payment.OrderPaymentReader;
import com.loopers.ordering.order.application.service.OrderCommandService;
import com.loopers.ordering.order.application.service.OrderQueryService;
import com.loopers.ordering.order.domain.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;


/**
 * 주문 만료 스케줄러
 * - 1분 주기로 만료 대상 주문을 조회하여 보상 트랜잭션 실행
 * - 만료 기준: PENDING_PAYMENT/PAYMENT_FAILED + 30분 초과
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

	// service
	private final OrderQueryService orderQueryService;
	private final OrderCommandService orderCommandService;
	// port
	private final OrderPaymentReader orderPaymentReader;

	// 만료 시간 (분)
	private static final int EXPIRATION_MINUTES = 30;


	/**
	 * 주문 만료 스케줄러
	 * 1. 만료 대상 주문 일괄 처리 (1분 주기 — 만료 조회 → 건별 보상 실행)
	 */

	// 1. 만료 대상 주문 일괄 처리 (1분 주기)
	@Scheduled(fixedDelay = 60000)
	public void expireOrders() {

		// 만료 기준 시간 계산
		LocalDateTime expirationThreshold = LocalDateTime.now().minusMinutes(EXPIRATION_MINUTES);

		// 만료 대상 주문 조회
		List<Order> expirableOrders = orderQueryService.findExpirableOrders(expirationThreshold);

		// 건별 처리 (한 건 실패가 다른 건에 영향 X)
		for (Order order : expirableOrders) {
			try {
				processExpiration(order);
			} catch (Exception e) {
				log.error("[OrderExpiration] orderId={} 만료 처리 실패 — 예외 타입: {}, 메시지: {}",
					order.getId(), e.getClass().getSimpleName(), e.getMessage(), e);
			}
		}
	}


	/**
	 * private method
	 * - 개별 주문 만료 처리
	 */

	// 개별 주문 만료 처리
	private void processExpiration(Order order) {

		// 진행 중인 결제(REQUESTED) 존재 시 skip
		if (orderPaymentReader.existsRequestedPayment(order.getId())) {
			log.info("[OrderExpiration] orderId={} — REQUESTED 결제 존재, skip", order.getId());
			return;
		}

		// 단일 TX 보상 실행 (재고 복원 + 쿠폰 복원 + EXPIRED 변경, CAS 방어)
		orderCommandService.expireOrder(order);

		log.info("[OrderExpiration] orderId={} 만료 처리 완료", order.getId());
	}

}
