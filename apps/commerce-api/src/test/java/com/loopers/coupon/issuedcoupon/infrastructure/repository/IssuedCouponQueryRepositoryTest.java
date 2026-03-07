package com.loopers.coupon.issuedcoupon.infrastructure.repository;


import com.loopers.coupon.issuedcoupon.domain.model.IssuedCoupon;
import com.loopers.coupon.issuedcoupon.domain.model.enums.IssuedCouponStatus;
import com.loopers.coupon.issuedcoupon.domain.repository.IssuedCouponCommandRepository;
import com.loopers.coupon.issuedcoupon.domain.repository.IssuedCouponQueryRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("IssuedCouponQueryRepository 통합 테스트")
class IssuedCouponQueryRepositoryTest {

	@Autowired
	private IssuedCouponQueryRepository issuedCouponQueryRepository;

	@Autowired
	private IssuedCouponCommandRepository issuedCouponCommandRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Nested
	@DisplayName("findByIdForUpdate()")
	class FindByIdForUpdateTest {

		@Test
		@Transactional
		@DisplayName("[findByIdForUpdate()] 존재하는 발급 쿠폰 ID -> 비관적 쓰기 락으로 발급 쿠폰 반환")
		void findByIdForUpdateSuccess() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 100L);
			IssuedCoupon saved = issuedCouponCommandRepository.save(issuedCoupon);

			// Act
			Optional<IssuedCoupon> result = issuedCouponQueryRepository.findByIdForUpdate(saved.getId());

			// Assert
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get().getId()).isEqualTo(saved.getId()),
				() -> assertThat(result.get().getStatus()).isEqualTo(IssuedCouponStatus.AVAILABLE)
			);
		}


		@Test
		@Transactional
		@DisplayName("[findByIdForUpdate()] 존재하지 않는 ID -> Optional.empty() 반환")
		void findByIdForUpdateNotFound() {
			// Act
			Optional<IssuedCoupon> result = issuedCouponQueryRepository.findByIdForUpdate(999L);

			// Assert
			assertThat(result).isEmpty();
		}

	}

}
