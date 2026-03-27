package com.loopers.coupon.application.port.out;


import com.loopers.coupon.application.dto.CouponTemplateInfo;

import java.util.Optional;


/**
 * 쿠폰 템플릿 조회 포트 (application → infrastructure 계약)
 * - 쿠폰 발급 처리 시 템플릿 존재/삭제/만료 검증용
 */
public interface CouponTemplateReaderPort {

	// 1. 템플릿 조회
	Optional<CouponTemplateInfo> findById(Long couponTemplateId);

}
