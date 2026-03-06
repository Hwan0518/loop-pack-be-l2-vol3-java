package com.loopers.coupon.issuedcoupon.domain.repository;


import com.loopers.coupon.issuedcoupon.domain.model.IssuedCoupon;


/**
 * 발급된 쿠폰 명령 리포지토리 인터페이스
 */
public interface IssuedCouponCommandRepository {

	// 1. 발급 쿠폰 저장
	IssuedCoupon save(IssuedCoupon issuedCoupon);

}
