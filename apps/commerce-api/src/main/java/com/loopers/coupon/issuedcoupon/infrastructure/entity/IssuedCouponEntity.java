package com.loopers.coupon.issuedcoupon.infrastructure.entity;


import com.loopers.coupon.issuedcoupon.domain.model.enums.IssuedCouponStatus;
import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * 발급된 쿠폰 엔티티
 * - couponTemplateId: 쿠폰 템플릿 ID
 * - userId: 사용자 ID
 * - status: 상태 (AVAILABLE / USED)
 * - 1인 1쿠폰: (user_id, coupon_template_id) 복합 유니크 제약이 DB 레벨에서 보장
 */
@Entity
@Table(name = "issued_coupon",
	uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "coupon_template_id"}),
	indexes = {
		// 사용자 쿠폰 내역: WHERE user_id = ? ORDER BY created_at DESC
		@Index(name = "idx_issued_coupon_user_created", columnList = "user_id, created_at"),
		// 관리자 쿠폰 발급 내역: WHERE coupon_template_id = ? ORDER BY created_at DESC
		@Index(name = "idx_issued_coupon_template_created", columnList = "coupon_template_id, created_at")
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssuedCouponEntity extends BaseEntity {

	@Column(name = "coupon_template_id", nullable = false)
	private Long couponTemplateId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private IssuedCouponStatus status;


	private IssuedCouponEntity(Long id, Long couponTemplateId, Long userId, IssuedCouponStatus status) {
		super(id);
		this.couponTemplateId = couponTemplateId;
		this.userId = userId;
		this.status = status;
	}


	// DB 복원용 (id 포함)
	public static IssuedCouponEntity of(Long id, Long couponTemplateId, Long userId, IssuedCouponStatus status) {
		return new IssuedCouponEntity(id, couponTemplateId, userId, status);
	}

	// 신규 생성용 (id = null)
	public static IssuedCouponEntity of(Long couponTemplateId, Long userId, IssuedCouponStatus status) {
		return of(null, couponTemplateId, userId, status);
	}

}
