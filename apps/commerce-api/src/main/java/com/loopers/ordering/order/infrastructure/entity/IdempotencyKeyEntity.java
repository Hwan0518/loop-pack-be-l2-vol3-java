package com.loopers.ordering.order.infrastructure.entity;


import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * 멱등성 키 엔티티
 * - userId: 사용자 ID
 * - requestId: 요청 ID
 * - orderId: 연결된 주문 ID
 */
@Entity
@Table(name = "idempotency_keys", uniqueConstraints = {
	@UniqueConstraint(columnNames = {"user_id", "request_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKeyEntity extends BaseEntity {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "request_id", nullable = false, length = 255)
	private String requestId;

	@Column(name = "order_id", nullable = false)
	private Long orderId;


	private IdempotencyKeyEntity(Long userId, String requestId, Long orderId) {
		this.userId = userId;
		this.requestId = requestId;
		this.orderId = orderId;
	}


	public static IdempotencyKeyEntity of(Long userId, String requestId, Long orderId) {
		return new IdempotencyKeyEntity(userId, requestId, orderId);
	}

}
