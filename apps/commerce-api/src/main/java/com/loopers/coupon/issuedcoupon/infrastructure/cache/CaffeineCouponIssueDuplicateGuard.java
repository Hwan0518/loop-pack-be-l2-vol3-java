package com.loopers.coupon.issuedcoupon.infrastructure.cache;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.loopers.coupon.issuedcoupon.application.port.out.cache.CouponIssueDuplicateGuard;
import org.springframework.stereotype.Component;

import java.time.Duration;


/**
 * Caffeine 기반 쿠폰 발급 중복 방어 로컬 캐시
 * - key: "userId:couponTemplateId"
 * - TTL 10분, 최대 10,000건
 * - putIfAbsent 기반 원자적 중복 차단
 */
@Component
public class CaffeineCouponIssueDuplicateGuard implements CouponIssueDuplicateGuard {

	// field
	private static final Boolean PRESENT = Boolean.TRUE;

	private final Cache<String, Boolean> cache = Caffeine.newBuilder()
		.expireAfterWrite(Duration.ofMinutes(10))
		.maximumSize(10_000)
		.build();


	// 1. 발급 락 획득 시도 (putIfAbsent — null이면 최초 요청)
	@Override
	public boolean tryAcquire(Long userId, Long couponTemplateId) {
		String key = buildKey(userId, couponTemplateId);
		return cache.asMap().putIfAbsent(key, PRESENT) == null;
	}


	// 캐시 키 생성
	private String buildKey(Long userId, Long couponTemplateId) {
		return userId + ":" + couponTemplateId;
	}

}
