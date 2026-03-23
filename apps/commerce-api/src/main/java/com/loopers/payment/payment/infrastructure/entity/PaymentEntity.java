package com.loopers.payment.payment.infrastructure.entity;


import com.loopers.domain.BaseEntity;
import com.loopers.payment.payment.domain.model.enums.CardType;
import com.loopers.payment.payment.domain.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


/**
 * 결제 엔티티
 * - userId: 결제 요청 사용자 ID
 * - orderId: 주문 ID
 * - cardType: 카드 타입
 * - cardNo: 카드번호
 * - amount: 결제 금액
 * - transactionKey: PG 트랜잭션 키
 * - status: 결제 상태
 * - failureReason: 결제 실패 사유
 */
@Entity
@Table(name = "payments",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_payments_transaction_key", columnNames = "transaction_key"),
		@UniqueConstraint(name = "uk_payments_pg_order_id", columnNames = "pg_order_id")
	},
	indexes = {
		@Index(name = "idx_payments_order_id", columnList = "order_id"),
		@Index(name = "idx_payments_pg_order_id", columnList = "pg_order_id"),
		@Index(name = "idx_payments_status_created", columnList = "status, created_at")
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEntity extends BaseEntity {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "order_id", nullable = false)
	private Long orderId;

	@Column(name = "pg_order_id", nullable = false, length = 80)
	private String pgOrderId;

	@Enumerated(EnumType.STRING)
	@Column(name = "card_type", nullable = false, length = 20)
	private CardType cardType;

	@Column(name = "card_no", nullable = false, length = 19)
	private String cardNo;

	@Column(name = "amount", nullable = false, precision = 15, scale = 2)
	private BigDecimal amount;

	@Column(name = "transaction_key", length = 100)
	private String transactionKey;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private PaymentStatus status;

	@Column(name = "failure_reason", length = 500)
	private String failureReason;


	private PaymentEntity(Long id, Long userId, Long orderId, String pgOrderId, CardType cardType, String cardNo,
		BigDecimal amount, String transactionKey, PaymentStatus status, String failureReason) {
		super(id);
		this.userId = userId;
		this.orderId = orderId;
		this.pgOrderId = pgOrderId;
		this.cardType = cardType;
		this.cardNo = cardNo;
		this.amount = amount;
		this.transactionKey = transactionKey;
		this.status = status;
		this.failureReason = failureReason;
	}


	// DB 복원용 (id 포함)
	public static PaymentEntity of(Long id, Long userId, Long orderId, String pgOrderId, CardType cardType, String cardNo,
		BigDecimal amount, String transactionKey, PaymentStatus status, String failureReason) {
		return new PaymentEntity(id, userId, orderId, pgOrderId, cardType, cardNo, amount, transactionKey, status, failureReason);
	}

	// 신규 생성용 (id = null)
	public static PaymentEntity of(Long userId, Long orderId, String pgOrderId, CardType cardType, String cardNo,
		BigDecimal amount, String transactionKey, PaymentStatus status, String failureReason) {
		return of(null, userId, orderId, pgOrderId, cardType, cardNo, amount, transactionKey, status, failureReason);
	}

}
