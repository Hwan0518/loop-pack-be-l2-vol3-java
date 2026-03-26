package com.loopers.coupon.infrastructure.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


/**
 * 발급 요청 상태 업데이트용 경량 엔티티 (coupon_issue_request)
 */

@Entity
@Table(name = "coupon_issue_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreamerCouponIssueRequestEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "request_id", nullable = false, length = 100)
	private String requestId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "coupon_template_id", nullable = false)
	private Long couponTemplateId;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(name = "reject_reason", length = 255)
	private String rejectReason;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;


	// 상태 업데이트
	public void markIssued() {
		this.status = "ISSUED";
		this.updatedAt = LocalDateTime.now();
	}

	public void reject(String reason) {
		this.status = "REJECTED";
		this.rejectReason = reason;
		this.updatedAt = LocalDateTime.now();
	}

}
