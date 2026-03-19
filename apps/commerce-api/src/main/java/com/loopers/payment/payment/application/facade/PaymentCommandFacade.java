package com.loopers.payment.payment.application.facade;


import com.loopers.payment.payment.application.dto.in.PaymentCreateInDto;
import com.loopers.payment.payment.application.dto.out.PaymentOutDto;
import com.loopers.payment.payment.application.dto.out.PgRecoveryResult;
import com.loopers.payment.payment.application.service.PaymentCommandService;
import com.loopers.payment.payment.application.service.PaymentQueryService;
import com.loopers.payment.payment.domain.model.Payment;
import com.loopers.payment.payment.domain.model.enums.PaymentStatus;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;


@Service
@RequiredArgsConstructor
public class PaymentCommandFacade {

	private static final int PG_RECOVERY_GRACE_SECONDS = 5;

	// service
	private final PaymentCommandService paymentCommandService;
	private final PaymentQueryService paymentQueryService;


	/**
	 * 결제 명령 파사드
	 * - @Transactional 없음: PG 호출이 TX 사이에 위치하므로 Service 레벨에서 독립 TX로 분리
	 * 1. 결제 요청 (TX-1 → PG 호출 → TX-2/TX-3)
	 * 2. 콜백 처리 (PG 콜백 수신)
	 * 3. 수동 복구 (관리자 수동 상태 반영)
	 */

	// 1. 결제 요청 (Facade TX 없음 — 각 Service 메서드가 독립 TX)
	public PaymentOutDto requestPayment(Long userId, PaymentCreateInDto inDto) {

		// [TX-1] Order 비관적 락 + 검증 + Payment 생성 (REQUESTED)
		Payment payment = paymentCommandService.createPaymentWithLock(
			userId, inDto.orderId(), inDto.cardType(), inDto.cardNo()
		);

		// PG 호출 (TX 바깥) — Service가 PG 예외를 CoreException으로 변환
		String transactionKey;
		try {
			transactionKey = paymentCommandService.requestPaymentAndGetTransactionKey(
				userId, payment.getPgOrderId(), payment.getCardType().name(),
				payment.getCardNo(), payment.getAmount()
			);
		} catch (CoreException e) {
			// 서킷 OPEN / PG 4xx → Payment REQUESTED 유지 (PG 호출 자체 안 했거나 클라이언트 오류이므로 failPayment 미호출)
			if (e.getErrorType() == ErrorType.PG_SERVICE_UNAVAILABLE || e.getErrorType() == ErrorType.PG_BAD_REQUEST) {
				throw e;
			}
			// Connection timeout(504) / HTTP 500(502) → Payment FAILED + Order PAYMENT_FAILED
			paymentCommandService.failPayment(payment.getId(), "PG 결제 처리 중 오류 발생");
			throw e;
		} catch (Exception e) {
			// 예상치 못한 예외 → Payment FAILED + 502
			paymentCommandService.failPayment(payment.getId(), "예상치 못한 오류 발생");
			throw new CoreException(ErrorType.PG_REQUEST_FAILED);
		}

		// Read timeout → Payment REQUESTED 유지, 504 반환 (폴링 스케줄러가 후속 처리)
		if (transactionKey == null) {
			throw new CoreException(ErrorType.PG_TIMEOUT);
		}

		// [TX-2] PG 성공 응답 → transactionKey 저장 (saveTransactionKey 실패는 PG 실패로 처리하지 않음)
		paymentCommandService.saveTransactionKey(payment.getId(), transactionKey);

		// 응답에 transactionKey 반영 (in-memory)
		payment.updateTransactionKey(transactionKey);

		return PaymentOutDto.from(payment);
	}


	// 2. 콜백 처리 (PG 콜백 수신)
	public void handleCallback(String transactionKey, String status, String reason) {

		// 트랜잭션 키로 결제 조회
		Optional<Payment> paymentOpt = paymentQueryService.findByTransactionKey(transactionKey);
		if (paymentOpt.isEmpty()) {
			throw new CoreException(ErrorType.PAYMENT_NOT_FOUND);
		}

		Payment payment = paymentOpt.get();

		// 이미 최종 상태 → skip (멱등, 정상 응답)
		if (payment.getStatus() != PaymentStatus.REQUESTED) {
			return;
		}

		// 결제 결과 반영 (단일 TX — Payment + Order 상태 동기화)
		paymentCommandService.updatePaymentResult(transactionKey, status, reason);
	}


	// 3. 수동 복구 (관리자 수동 상태 반영)
	public PaymentOutDto recoverPayment(Long paymentId) {

		// 결제 조회
		Payment payment = paymentQueryService.findById(paymentId)
			.orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));

		// 이미 최종 상태 → skip
		if (payment.getStatus() != PaymentStatus.REQUESTED) {
			return PaymentOutDto.from(payment);
		}

		// PG 조회 + 상태 반영 (Service가 infrastructure DTO를 application DTO로 변환)
		try {
			PgRecoveryResult result = paymentCommandService.recoverPaymentStatus(payment);

			if (!result.found()) {
				// Read timeout 직후에는 PG 트랜잭션이 아직 저장되지 않았을 수 있다.
				if (isWithinRecoveryGracePeriod(payment)) {
					return PaymentOutDto.from(payment);
				}
				// PG에 트랜잭션 없음 → FAILED
				paymentCommandService.failPayment(payment.getId(), "PG에 트랜잭션이 존재하지 않습니다.");
			} else if (result.byTransactionKey()) {
				// transactionKey 기반 결과 반영
				paymentCommandService.updatePaymentResult(result.transactionKey(), result.status(), result.reason());
			} else {
				// orderId 기반 결과 반영
				paymentCommandService.updatePaymentResultByOrderId(
					payment.getOrderId(), result.transactionKey(), result.status(), result.reason()
				);
			}
		} catch (Exception e) {
			// PG 조회 실패 → 그대로 유지
			return PaymentOutDto.from(payment);
		}

		// 갱신된 Payment 반환
		return paymentQueryService.findById(paymentId)
			.map(PaymentOutDto::from)
			.orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
	}


	/**
	 * private method
	 * - 복구 유예 기간 내 여부 확인 (Read timeout 직후 PG 트랜잭션 미저장 대비)
	 */

	// 복구 유예 기간 내 여부 확인
	private boolean isWithinRecoveryGracePeriod(Payment payment) {
		return payment.getTransactionKey() == null
			&& payment.getCreatedAt() != null
			&& payment.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(PG_RECOVERY_GRACE_SECONDS));
	}

}
