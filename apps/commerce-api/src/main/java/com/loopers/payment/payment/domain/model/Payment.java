package com.loopers.payment.payment.domain.model;


import com.loopers.payment.payment.domain.model.enums.CardType;
import com.loopers.payment.payment.domain.model.enums.PaymentStatus;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;


@Getter
public class Payment {

	/**
	 * 결제
	 * - id: 결제 ID
	 * - userId: 결제 요청 사용자 ID
	 * - orderId: 주문 ID
	 * - pgOrderId: PG 전용 주문 식별자 (결제 시도 단위 유일, PG에 orderId로 전송)
	 * - cardType: 카드 타입
	 * - cardNo: 카드번호 (XXXX-XXXX-XXXX-XXXX)
	 * - amount: 결제 금액
	 * - transactionKey: PG 트랜잭션 키 (PG 응답 후 설정)
	 * - status: 결제 상태
	 * - failureReason: 결제 실패 사유 (실패 시에만 설정)
	 * - createdAt: 결제 생성 일시
	 */

	// immutable
	private final Long id;
	private final Long userId;
	private final Long orderId;
	private final String pgOrderId;
	private final CardType cardType;
	private final String cardNo;
	private final BigDecimal amount;
	private final LocalDateTime createdAt;

	// mutable
	private String transactionKey;
	private PaymentStatus status;
	private String failureReason;

	// 카드번호 형식 패턴
	private static final Pattern CARD_NO_PATTERN = Pattern.compile("^\\d{4}-\\d{4}-\\d{4}-\\d{4}$");


	// 생성자
	private Payment(Long id, Long userId, Long orderId, String pgOrderId, CardType cardType, String cardNo,
		BigDecimal amount, String transactionKey, PaymentStatus status, String failureReason,
		LocalDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.orderId = orderId;
		this.pgOrderId = pgOrderId;
		this.cardType = cardType;
		this.cardNo = cardNo;
		this.amount = amount;
		this.transactionKey = transactionKey;
		this.status = status;
		this.failureReason = failureReason;
		this.createdAt = createdAt;
	}


	/**
	 * 도메인 로직
	 * 1. 결제 생성
	 * 2. 결제 재생성 (Entity -> Model 매핑용도)
	 * 3. PG 트랜잭션 키 설정
	 * 4. 결제 성공 처리
	 * 5. 결제 실패 처리
	 */

	// 1. 결제 생성 (pgOrderId: 결제 시도 단위 유일 식별자 — PG에 orderId로 전송)
	public static Payment create(Long userId, Long orderId, CardType cardType, String cardNo,
		BigDecimal amount) {

		// 필수값 null 검증
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(orderId, "orderId");
		Objects.requireNonNull(cardType, "cardType");
		Objects.requireNonNull(amount, "amount");

		// 금액 검증
		validateAmount(amount);

		// 카드번호 형식 검증
		validateCardNo(cardNo);

		// PG 전용 주문 식별자 생성 (결제 시도마다 유일 — 재결제 시 PG orderId 기반 조회에서 정확히 1건 매칭)
		String pgOrderId = generatePgOrderId(orderId);

		return new Payment(null, userId, orderId, pgOrderId, cardType, cardNo, amount, null, PaymentStatus.REQUESTED, null, null);
	}


	// 2. 결제 재생성 (Entity -> Model 매핑용도)
	public static Payment reconstruct(Long id, Long userId, Long orderId, String pgOrderId, CardType cardType, String cardNo,
		BigDecimal amount, String transactionKey, PaymentStatus status, String failureReason,
		LocalDateTime createdAt) {
		return new Payment(id, userId, orderId, pgOrderId, cardType, cardNo, amount, transactionKey, status, failureReason, createdAt);
	}


	// 3. PG 트랜잭션 키 설정
	public void updateTransactionKey(String key) {
		if (key == null || key.isBlank()) {
			throw new CoreException(ErrorType.BAD_REQUEST, "트랜잭션 키는 빈 값일 수 없습니다.");
		}
		this.transactionKey = key;
	}


	// 4. 결제 성공 처리
	public void succeed() {

		// 상태 전이 검증
		validateTransition(PaymentStatus.SUCCESS);

		this.status = PaymentStatus.SUCCESS;
	}


	// 5. 결제 실패 처리
	public void fail(String reason) {

		// 상태 전이 검증
		validateTransition(PaymentStatus.FAILED);

		this.status = PaymentStatus.FAILED;
		this.failureReason = reason;
	}


	/**
	 * private method
	 * - PG 전용 주문 식별자 생성
	 * - 금액 검증
	 * - 카드번호 형식 검증
	 * - 상태 전이 검증
	 */

	// PG 전용 주문 식별자 생성 (PGO-{orderId}-{uuid32} — 결제 시도마다 유일)
	private static String generatePgOrderId(Long orderId) {
		return "PGO-" + orderId + "-" + UUID.randomUUID().toString().replace("-", "");
	}


	// 금액 검증 (0 이하 불가)
	private static void validateAmount(BigDecimal amount) {
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
		}
	}

	// 카드번호 형식 검증
	private static void validateCardNo(String cardNo) {
		if (cardNo == null || !CARD_NO_PATTERN.matcher(cardNo).matches()) {
			throw new CoreException(ErrorType.INVALID_CARD_NO);
		}
	}

	// 상태 전이 검증
	private void validateTransition(PaymentStatus target) {
		if (!this.status.canTransitionTo(target)) {
			throw new CoreException(ErrorType.BAD_REQUEST, "결제 상태를 변경할 수 없습니다.");
		}
	}

}
