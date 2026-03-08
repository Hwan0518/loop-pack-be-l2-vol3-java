package com.loopers.coupon.issuedcoupon.application.dto.out;


import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
import com.loopers.coupon.issuedcoupon.domain.model.IssuedCoupon;
import com.loopers.coupon.issuedcoupon.domain.model.enums.CouponStatusView;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * 발급된 쿠폰 응답 OutDto (사용자)
 */
public record IssuedCouponOutDto(
	Long issuedCouponId,
	String templateName,
	CouponType type,
	BigDecimal value,
	BigDecimal minOrderAmount,
	LocalDateTime expiredAt,
	CouponStatusView status
) {

	// IssuedCoupon + CouponTemplate 도메인 객체를 OutDto로 변환
	public static IssuedCouponOutDto from(IssuedCoupon issuedCoupon, CouponTemplate template) {
		return new IssuedCouponOutDto(
			issuedCoupon.getId(),
			template.getName(),
			template.getType(),
			template.getValue(),
			template.getMinOrderAmount(),
			template.getExpiredAt(),
			issuedCoupon.getStatusView(template)
		);
	}

}
