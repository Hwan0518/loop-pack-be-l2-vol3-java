package com.loopers.payment.payment.application.service;


import com.loopers.payment.payment.application.dto.out.PgRecoveryResult;
import com.loopers.payment.payment.application.port.out.client.order.PaymentOrderInfo;
import com.loopers.payment.payment.application.port.out.client.order.PaymentOrderItemInfo;
import com.loopers.payment.payment.application.port.out.client.order.PaymentOrderReader;
import com.loopers.payment.payment.application.port.out.client.order.PaymentOrderStatusManager;
import com.loopers.payment.payment.application.port.out.client.pg.PgPaymentGateway;
import com.loopers.payment.payment.application.port.out.client.pg.dto.PgPaymentListResponse;
import com.loopers.payment.payment.application.port.out.client.pg.dto.PgPaymentRequest;
import com.loopers.payment.payment.application.port.out.client.pg.dto.PgPaymentResponse;
import com.loopers.payment.payment.application.port.out.client.pg.exception.PgBadRequestException;
import com.loopers.payment.payment.application.port.out.client.pg.exception.PgConnectionTimeoutException;
import com.loopers.payment.payment.application.port.out.client.pg.exception.PgReadTimeoutException;
import com.loopers.payment.payment.application.port.out.client.pg.exception.PgRequestFailedException;
import com.loopers.payment.payment.domain.model.Payment;
import com.loopers.payment.payment.domain.model.enums.CardType;
import com.loopers.payment.payment.domain.model.enums.PaymentStatus;
import com.loopers.payment.payment.domain.repository.PaymentCommandRepository;
import com.loopers.payment.payment.domain.repository.PaymentQueryRepository;
import com.loopers.payment.payment.application.dto.out.PaymentOrderItemPayload;
import com.loopers.payment.payment.application.dto.out.PaymentOrderPaidPayload;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.support.common.outbox.application.port.OutboxEventPort;
import com.loopers.support.common.outbox.application.util.JsonSerializer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;


@Service
public class PaymentCommandService {

	// repository
	private final PaymentCommandRepository paymentCommandRepository;
	private final PaymentQueryRepository paymentQueryRepository;
	// port
	private final PaymentOrderReader paymentOrderReader;
	private final PaymentOrderStatusManager paymentOrderStatusManager;
	private final PgPaymentGateway pgPaymentGateway;
	// outbox
	private final OutboxEventPort outboxEventPort;
	private final JsonSerializer jsonSerializer;
	// config
	private final String callbackUrl;

	public PaymentCommandService(
		PaymentCommandRepository paymentCommandRepository,
		PaymentQueryRepository paymentQueryRepository,
		PaymentOrderReader paymentOrderReader,
		PaymentOrderStatusManager paymentOrderStatusManager,
		PgPaymentGateway pgPaymentGateway,
		OutboxEventPort outboxEventPort,
		JsonSerializer jsonSerializer,
		@Value("${payment.pg.callback-url}") String callbackUrl
	) {
		this.paymentCommandRepository = paymentCommandRepository;
		this.paymentQueryRepository = paymentQueryRepository;
		this.paymentOrderReader = paymentOrderReader;
		this.paymentOrderStatusManager = paymentOrderStatusManager;
		this.pgPaymentGateway = pgPaymentGateway;
		this.outboxEventPort = outboxEventPort;
		this.jsonSerializer = jsonSerializer;
		this.callbackUrl = callbackUrl;
	}


	/**
	 * 결제 명령 서비스
	 * 1. 결제 생성 (TX-1: Order FOR UPDATE + 멱등성 + Payment 생성)
	 * 2. PG 트랜잭션 키 저장 (TX-2: PG 성공 응답 후)
	 * 3. 결제 실패 처리 (TX-3: PG 실패 시 Payment FAILED + Order PAYMENT_FAILED)
	 * 4. 결제 결과 반영 — transactionKey 기반 (콜백/폴링)
	 * 5. 결제 결과 반영 — orderId 기반 (폴링 시 transactionKey 없는 Payment)
	 * 6. PG 결제 요청 + transactionKey 반환 (TX 없음 — PG 예외를 CoreException으로 변환)
	 * 7. PG 조회 → 복구 결과 반환 — transactionKey 기반 (TX 없음)
	 * 8. PG 조회 → 복구 결과 반환 — pgOrderId 기반 (TX 없음)
	 * 9. PG 조회 → 복구 결과 반환 — Payment 정보 기반 자동 판단 (TX 없음 — Facade/Scheduler 공용)
	 */

	// 1. 결제 생성 (TX-1: Facade의 첫 번째 TX)
	@Transactional
	public Payment createPaymentWithLock(Long userId, Long orderId, String cardType, String cardNo) {

		// Order 비관적 락 조회 + 사용자 검증
		PaymentOrderInfo orderInfo = paymentOrderReader.findOrderForPayment(orderId, userId);

		// 주문 상태 검증 (PENDING_PAYMENT / PAYMENT_FAILED만 결제 가능)
		if (!orderInfo.isPayable()) {
			throw new CoreException(ErrorType.ORDER_NOT_PAYABLE);
		}

		// 멱등성 체크: REQUESTED 상태 Payment 존재 시 이미 진행 중
		Optional<Payment> existingRequested = paymentQueryRepository.findByOrderIdAndStatus(orderId, PaymentStatus.REQUESTED);
		if (existingRequested.isPresent()) {
			throw new CoreException(ErrorType.PAYMENT_ALREADY_IN_PROGRESS);
		}

		// 카드 타입 변환 (유효하지 않은 카드 타입 시 INVALID_CARD_TYPE 예외)
		CardType resolvedCardType = CardType.from(cardType);

		// Payment 생성 (REQUESTED)
		Payment payment = Payment.create(userId, orderId, resolvedCardType, cardNo, orderInfo.totalPrice());
		Payment savedPayment = paymentCommandRepository.save(payment);

		// 재결제인 경우 Order 상태를 PENDING_PAYMENT로 변경
		if (orderInfo.isPaymentFailed()) {
			paymentOrderStatusManager.markOrderPendingPayment(orderId);
		}

		return savedPayment;
	}


	// 2. PG 트랜잭션 키 저장 (TX-2: PG 성공 응답 후)
	@Transactional
	public void saveTransactionKey(Long paymentId, String transactionKey) {

		// 결제 조회
		Payment payment = paymentQueryRepository.findById(paymentId)
			.orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));

		// 트랜잭션 키 저장
		payment.updateTransactionKey(transactionKey);
		paymentCommandRepository.save(payment);
	}


	// 3. 결제 실패 처리 (TX-3: PG 실패 시)
	@Transactional
	public void failPayment(Long paymentId, String reason) {

		// 결제 조회
		Payment payment = paymentQueryRepository.findById(paymentId)
			.orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));

		// 이미 최종 상태면 skip (멱등)
		if (payment.getStatus() != PaymentStatus.REQUESTED) {
			return;
		}

		// Payment 실패 처리
		payment.fail(reason);
		paymentCommandRepository.save(payment);

		// Order 상태 PAYMENT_FAILED로 변경
		paymentOrderStatusManager.markOrderPaymentFailed(payment.getOrderId());
	}


	// 4. 결제 결과 반영 — transactionKey 기반 (콜백/폴링)
	@Transactional
	public void updatePaymentResult(String transactionKey, String pgStatus, String reason) {

		// 트랜잭션 키로 결제 조회
		Payment payment = paymentQueryRepository.findByTransactionKey(transactionKey)
			.orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));

		// 이미 최종 상태면 skip (멱등)
		if (payment.getStatus() != PaymentStatus.REQUESTED) {
			return;
		}

		// PG 상태에 따른 처리
		applyPgResult(payment, pgStatus, reason);
	}


	// 5. 결제 결과 반영 — orderId 기반 (폴링 시 transactionKey 없는 Payment)
	@Transactional
	public void updatePaymentResultByOrderId(Long orderId, String transactionKey, String pgStatus, String reason) {

		// orderId로 REQUESTED 결제 조회
		Payment payment = paymentQueryRepository.findRequestedByOrderId(orderId)
			.orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));

		// 트랜잭션 키 설정 (PG 응답에서 획득)
		if (transactionKey != null && !transactionKey.isBlank()) {
			payment.updateTransactionKey(transactionKey);
		}

		// PG 상태에 따른 처리
		applyPgResult(payment, pgStatus, reason);
	}


	// 6. PG 결제 요청 + transactionKey 반환 (TX 없음 — PG 예외를 CoreException으로 변환)
	public String requestPaymentAndGetTransactionKey(Long userId, String pgOrderId, String cardType, String cardNo, BigDecimal amount) {
		try {
			PgPaymentResponse response = requestPayment(userId, pgOrderId, cardType, cardNo, amount);
			return response.data().transactionKey();
		} catch (PgBadRequestException e) {
			// 4xx: 클라이언트 오류 → 400 (서킷/재시도 대상 아님)
			throw new CoreException(ErrorType.PG_BAD_REQUEST);
		} catch (PgReadTimeoutException e) {
			// Read timeout → null 반환 (Payment REQUESTED 유지, 폴링 스케줄러가 후속 처리)
			return null;
		} catch (CallNotPermittedException e) {
			// 서킷 OPEN → 503 (PG 호출 자체 안 했으므로 Payment REQUESTED 유지)
			throw new CoreException(ErrorType.PG_SERVICE_UNAVAILABLE);
		} catch (PgConnectionTimeoutException e) {
			// Connection timeout (Retry 소진) → 504 (요청 미도달 → Payment FAILED 처리 필요)
			throw new CoreException(ErrorType.PG_TIMEOUT);
		} catch (PgRequestFailedException e) {
			// PG HTTP 500 (Retry 소진) → 502 (명시적 거부 → Payment FAILED 처리 필요)
			throw new CoreException(ErrorType.PG_REQUEST_FAILED);
		}
	}


	// 7. PG 조회 → 복구 결과 반환 (transactionKey 기반 단건 조회, TX 없음)
	public PgRecoveryResult queryByTransactionKey(Long userId, String transactionKey) {
		try {
			// transactionKey로 PG 단건 조회
			PgPaymentResponse pgResponse = pgPaymentGateway.getPaymentByTransactionKey(userId, transactionKey);

			return new PgRecoveryResult(
				true, transactionKey,
				pgResponse.data().status(), pgResponse.data().reason(), true
			);
		} catch (PgBadRequestException e) {
			// 4xx: 클라이언트 오류 → 400
			throw new CoreException(ErrorType.PG_BAD_REQUEST);
		}
	}


	// 8. PG 조회 → 복구 결과 반환 (pgOrderId 기반 목록 조회, TX 없음)
	public PgRecoveryResult queryByPgOrderId(Long userId, String pgOrderId) {
		try {
			// pgOrderId로 PG 목록 조회
			PgPaymentListResponse pgListResponse = pgPaymentGateway.getPaymentsByOrderId(userId, pgOrderId);

			// PG에 트랜잭션 없음
			if (pgListResponse.data() == null || pgListResponse.data().transactions() == null
				|| pgListResponse.data().transactions().isEmpty()) {
				return PgRecoveryResult.notFound();
			}

			// pgOrderId가 결제 시도 단위 유일이므로, 조회 결과는 해당 시도에 대응 → 첫 번째 트랜잭션 사용
			PgPaymentListResponse.Transaction tx = pgListResponse.data().transactions().get(0);

			return new PgRecoveryResult(true, tx.transactionKey(), tx.status(), tx.reason(), false);
		} catch (PgBadRequestException e) {
			// 4xx: 클라이언트 오류 → 400
			throw new CoreException(ErrorType.PG_BAD_REQUEST);
		}
	}


	// 9. PG 조회 → 복구 결과 반환 (Payment 정보 기반 자동 판단 — Facade/Scheduler 공용, TX 없음)
	public PgRecoveryResult recoverPaymentStatus(Payment payment) {

		if (payment.getTransactionKey() != null) {
			// transactionKey 존재 → 단건 조회
			return queryByTransactionKey(payment.getUserId(), payment.getTransactionKey());
		} else {
			// transactionKey 없음 (Read timeout) → pgOrderId로 목록 조회
			return queryByPgOrderId(payment.getUserId(), payment.getPgOrderId());
		}
	}


	/**
	 * private method
	 * - PG 결과 적용 (SUCCESS/FAILED → Payment + Order 상태 동기화)
	 * - PG 결제 요청 (pgOrderId를 PG의 orderId로 전송)
	 */

	// PG 결과 적용 (SUCCESS/FAILED → Payment + Order 상태 동기화)
	private void applyPgResult(Payment payment, String pgStatus, String reason) {

		if ("SUCCESS".equals(pgStatus)) {
			// 결제 성공
			payment.succeed();
			paymentCommandRepository.save(payment);
			paymentOrderStatusManager.markOrderPaid(payment.getOrderId());

			// 주문 항목 조회 (결제 성공 시에만 — 불필요한 조회 방지)
			List<PaymentOrderItemInfo> orderItems = paymentOrderReader.findOrderItems(payment.getOrderId());

			// Outbox 저장 (같은 TX — At Least Once 보장)
			List<PaymentOrderItemPayload> itemPayloads = orderItems.stream()
				.map(item -> new PaymentOrderItemPayload(item.productId(), item.quantity()))
				.toList();
			outboxEventPort.save("ORDER", String.valueOf(payment.getOrderId()), "ORDER_PAID",
				"order-events", String.valueOf(payment.getOrderId()),
				jsonSerializer.toJson(new PaymentOrderPaidPayload(
					payment.getOrderId(), payment.getUserId(), payment.getId(),
					itemPayloads, payment.getAmount(), java.time.LocalDateTime.now()
				)));

		} else if ("FAILED".equals(pgStatus)) {
			// 결제 실패
			payment.fail(reason);
			paymentCommandRepository.save(payment);
			paymentOrderStatusManager.markOrderPaymentFailed(payment.getOrderId());
		}
		// PENDING → skip (다음 폴링 주기에 다시 확인)
	}


	// PG 결제 요청 (pgOrderId를 PG의 orderId로 전송)
	private PgPaymentResponse requestPayment(Long userId, String pgOrderId, String cardType, String cardNo, BigDecimal amount) {

		// PG 요청 DTO 구성
		PgPaymentRequest pgRequest = new PgPaymentRequest(
			pgOrderId,
			cardType,
			cardNo,
			amount.longValue(),
			callbackUrl
		);

		// PG 결제 요청
		return pgPaymentGateway.requestPayment(userId, pgRequest);
	}

}
