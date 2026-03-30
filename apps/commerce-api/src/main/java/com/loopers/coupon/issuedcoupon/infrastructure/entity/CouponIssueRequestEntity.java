package com.loopers.coupon.issuedcoupon.infrastructure.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


/**
 * 쿠폰 발급 요청 엔티티 (coupon_issue_request)
 */

@Entity
@Table(name = "coupon_issue_request", indexes = {
	@Index(name = "idx_cir_request_id", columnList = "request_id", unique = true),
	@Index(name = "idx_cir_user_template", columnList = "user_id, coupon_template_id"),
	@Index(name = "idx_cir_status_created", columnList = "status, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueRequestEntity {

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


	// 팩토리 메서드
	public static CouponIssueRequestEntity of(String requestId, Long userId, Long couponTemplateId, String status) {
		CouponIssueRequestEntity entity = new CouponIssueRequestEntity();
		entity.requestId = requestId;
		entity.userId = userId;
		entity.couponTemplateId = couponTemplateId;
		entity.status = status;
		entity.createdAt = LocalDateTime.now();
		entity.updatedAt = LocalDateTime.now();
		return entity;
	}


	// 상태 업데이트
	public void updateStatus(String status, String rejectReason) {
		this.status = status;
		this.rejectReason = rejectReason;
		this.updatedAt = LocalDateTime.now();
	}

}
