package com.loopers.coupon.coupontemplate.application.dto.out;


import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;


/**
 * 쿠폰 템플릿 응답 OutDto (관리자)
 */
public record AdminCouponTemplateOutDto(
	Long id,
	String name,
	CouponType type,
	BigDecimal value,
	BigDecimal minOrderAmount,
	LocalDateTime expiredAt,
	ZonedDateTime deletedAt
) {

	// CouponTemplate 도메인 객체를 OutDto로 변환
	public static AdminCouponTemplateOutDto from(CouponTemplate template) {
		return new AdminCouponTemplateOutDto(
			template.getId(),
			template.getName(),
			template.getType(),
			template.getValue(),
			template.getMinOrderAmount(),
			template.getExpiredAt(),
			template.getDeletedAt()
		);
	}

}
