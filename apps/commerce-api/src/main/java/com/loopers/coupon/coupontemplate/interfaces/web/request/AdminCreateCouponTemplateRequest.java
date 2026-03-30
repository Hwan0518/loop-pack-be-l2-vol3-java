package com.loopers.coupon.coupontemplate.interfaces.web.request;


import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * 쿠폰 템플릿 생성 요청 (관리자)
 */
public record AdminCreateCouponTemplateRequest(
	@NotBlank(message = "쿠폰 이름은 필수입니다.")
	String name,

	@NotNull(message = "쿠폰 타입은 필수입니다.")
	CouponType type,

	@NotNull(message = "할인 값은 필수입니다.")
	BigDecimal value,

	BigDecimal minOrderAmount,

	Integer maxQuantity,

	@NotNull(message = "만료일은 필수입니다.")
	LocalDateTime expiredAt
) {
}
