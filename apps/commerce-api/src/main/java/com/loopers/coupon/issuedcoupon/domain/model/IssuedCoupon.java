package com.loopers.coupon.issuedcoupon.domain.model;


import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.issuedcoupon.domain.model.enums.CouponStatusView;
import com.loopers.coupon.issuedcoupon.domain.model.enums.IssuedCouponStatus;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Objects;


@Getter
public class IssuedCoupon {

	/**
	 * 발급된 쿠폰
	 * - id: 발급 쿠폰 ID
	 * - couponTemplateId: 쿠폰 템플릿 ID
	 * - userId: 사용자 ID
	 * - status: 상태 (AVAILABLE / USED, DB 저장용)
	 */

	private final Long id;
	private final Long couponTemplateId;
	private final Long userId;
	private IssuedCouponStatus status;
	private final ZonedDateTime createdAt;


	// 생성자
	private IssuedCoupon(Long id, Long couponTemplateId, Long userId, IssuedCouponStatus status,
		ZonedDateTime createdAt) {
		this.id = id;
		this.couponTemplateId = couponTemplateId;
		this.userId = userId;
		this.status = status;
		this.createdAt = createdAt;
	}


	/**
	 * 도메인 로직
	 * 1. 발급 쿠폰 생성
	 * 2. 발급 쿠폰 재생성 (Entity -> Model 매핑용도)
	 * 3. 만료 여부 확인 (동적 계산)
	 * 4. 상태 뷰 반환 (API 응답용)
	 * 5. 쿠폰 사용 처리
	 * 6. 쿠폰 복원 (보상 트랜잭션 — 주문 만료 시 USED → AVAILABLE)
	 */

	// 1. 발급 쿠폰 생성 (1인 1쿠폰 — (user_id, coupon_template_id) 복합 유니크 제약이 DB 레벨에서 보장)
	public static IssuedCoupon create(Long couponTemplateId, Long userId) {
		Objects.requireNonNull(couponTemplateId, "couponTemplateId");
		Objects.requireNonNull(userId, "userId");
		return new IssuedCoupon(null, couponTemplateId, userId, IssuedCouponStatus.AVAILABLE, null);
	}


	// 2. 발급 쿠폰 재생성 (Entity -> Model 매핑용도)
	public static IssuedCoupon reconstruct(Long id, Long couponTemplateId, Long userId, IssuedCouponStatus status,
		ZonedDateTime createdAt) {
		return new IssuedCoupon(id, couponTemplateId, userId, status, createdAt);
	}


	// 3. 만료 여부 확인 (expiredAt < now 또는 template이 삭제된 경우)
	public boolean isExpired(CouponTemplate template) {
		return LocalDateTime.now().isAfter(template.getExpiredAt()) || template.isDeleted();
	}


	// 4. API 응답용 상태 뷰 반환
	public CouponStatusView getStatusView(CouponTemplate template) {
		if (isExpired(template)) {
			return CouponStatusView.EXPIRED;
		}
		if (status == IssuedCouponStatus.USED) {
			return CouponStatusView.USED;
		}
		return CouponStatusView.AVAILABLE;
	}


	// 5. 쿠폰 사용 처리 (상태 USED로 변경)
	public void use() {
		this.status = IssuedCouponStatus.USED;
	}


	// 6. 쿠폰 복원 (보상 트랜잭션 — USED → AVAILABLE, USED 아닌 상태에서 호출 시 예외)
	public void restore() {
		if (this.status != IssuedCouponStatus.USED) {
			throw new CoreException(ErrorType.BAD_REQUEST);
		}
		this.status = IssuedCouponStatus.AVAILABLE;
	}

}
