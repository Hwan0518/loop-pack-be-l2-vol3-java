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


	@Nested
	@DisplayName("release() 테스트")
	class ReleaseTest {

		@Test
		@DisplayName("[release()] 캐시에 존재하는 키 해제 -> 동일 키로 tryAcquire 재호출 시 true 반환. 발급 실패 후 재시도 허용")
		void releaseExistingKeyAllowsReacquire() {
			// Arrange — 먼저 캐시 점유
			cache.tryAcquire(100L, 1L);

			// Act — 캐시 해제
			cache.release(100L, 1L);

			// Assert — 재획득 가능
			assertThat(cache.tryAcquire(100L, 1L)).isTrue();
		}


		@Test
		@DisplayName("[release()] 캐시에 없는 키 해제 -> 예외 없이 정상 처리. 방어적 호출에도 안전")
		void releaseNonExistingKeyDoesNotThrow() {
			// Act & Assert — 캐시에 없는 키를 해제해도 예외 없음
			org.junit.jupiter.api.Assertions.assertDoesNotThrow(
				() -> cache.release(999L, 999L)
			);
		}


		@Test
		@DisplayName("[release()] 특정 키만 해제 -> 다른 키는 여전히 차단. 키별 독립성 보장")
		void releaseOnlyAffectsTargetKey() {
			// Arrange — 두 개의 키 점유
			cache.tryAcquire(100L, 1L);
			cache.tryAcquire(100L, 2L);

			// Act — 첫 번째 키만 해제
			cache.release(100L, 1L);

			// Assert — 해제된 키는 재획득 가능, 다른 키는 여전히 차단
			assertThat(cache.tryAcquire(100L, 1L)).isTrue();
			assertThat(cache.tryAcquire(100L, 2L)).isFalse();
		}

	}

}
