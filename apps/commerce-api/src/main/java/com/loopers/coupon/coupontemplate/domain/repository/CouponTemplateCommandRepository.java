package com.loopers.coupon.coupontemplate.domain.repository;


import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;


/**
 * 쿠폰 템플릿 명령 리포지토리 인터페이스
 */
public interface CouponTemplateCommandRepository {

	// 1. 쿠폰 템플릿 저장
	CouponTemplate save(CouponTemplate couponTemplate);

}
