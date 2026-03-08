package com.loopers.coupon.issuedcoupon.infrastructure.cache;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


@DisplayName("CaffeineCouponIssueDuplicateGuard 단위 테스트")
class CaffeineCouponIssueDuplicateGuardTest {

	private CaffeineCouponIssueDuplicateGuard cache;


	@BeforeEach
	void setUp() {
		cache = new CaffeineCouponIssueDuplicateGuard();
	}


	@Nested
	@DisplayName("tryAcquire() 테스트")
	class TryAcquireTest {

		@Test
		@DisplayName("[tryAcquire()] 최초 요청 -> true 반환. 캐시에 키가 없으면 획득 성공")
		void firstRequestReturnsTrue() {
			// Act
			boolean result = cache.tryAcquire(100L, 1L);

			// Assert
			assertThat(result).isTrue();
		}


		@Test
		@DisplayName("[tryAcquire()] 동일 userId + couponTemplateId 재요청 -> false 반환. 캐시에 키가 이미 존재하면 획득 실패")
		void duplicateRequestReturnsFalse() {
			// Arrange
			cache.tryAcquire(100L, 1L);

			// Act
			boolean result = cache.tryAcquire(100L, 1L);

			// Assert
			assertThat(result).isFalse();
		}


		@Test
		@DisplayName("[tryAcquire()] 다른 userId 동일 couponTemplateId -> true 반환. 사용자별로 독립적으로 관리")
		void differentUserReturnsTrue() {
			// Arrange
			cache.tryAcquire(100L, 1L);

			// Act
			boolean result = cache.tryAcquire(200L, 1L);

			// Assert
			assertThat(result).isTrue();
		}


		@Test
		@DisplayName("[tryAcquire()] 동일 userId 다른 couponTemplateId -> true 반환. 템플릿별로 독립적으로 관리")
		void differentTemplateReturnsTrue() {
			// Arrange
			cache.tryAcquire(100L, 1L);

			// Act
			boolean result = cache.tryAcquire(100L, 2L);

			// Assert
			assertThat(result).isTrue();
		}

	}

}
