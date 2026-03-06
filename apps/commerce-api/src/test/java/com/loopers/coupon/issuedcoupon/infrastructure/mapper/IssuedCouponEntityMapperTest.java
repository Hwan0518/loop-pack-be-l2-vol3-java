package com.loopers.coupon.issuedcoupon.infrastructure.mapper;


import com.loopers.coupon.issuedcoupon.domain.model.IssuedCoupon;
import com.loopers.coupon.issuedcoupon.domain.model.enums.IssuedCouponStatus;
import com.loopers.coupon.issuedcoupon.infrastructure.entity.IssuedCouponEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@DisplayName("IssuedCouponEntityMapper 단위 테스트")
class IssuedCouponEntityMapperTest {

	private IssuedCouponEntityMapper mapper;


	@BeforeEach
	void setUp() {
		mapper = new IssuedCouponEntityMapper();
	}


	@Nested
	@DisplayName("toEntity()")
	class ToEntityTest {

		@Test
		@DisplayName("[toEntity()] 기존 IssuedCoupon (id 포함) -> Entity 변환. 모든 필드가 일치한다")
		void toEntityWithId() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.reconstruct(
				1L, 10L, 100L, IssuedCouponStatus.AVAILABLE, ZonedDateTime.now()
			);

			// Act
			IssuedCouponEntity entity = mapper.toEntity(issuedCoupon);

			// Assert
			assertAll(
				() -> assertThat(entity.getId()).isEqualTo(1L),
				() -> assertThat(entity.getCouponTemplateId()).isEqualTo(10L),
				() -> assertThat(entity.getUserId()).isEqualTo(100L),
				() -> assertThat(entity.getStatus()).isEqualTo(IssuedCouponStatus.AVAILABLE)
			);
		}


		@Test
		@DisplayName("[toEntity()] 신규 IssuedCoupon (id=null) -> Entity 변환. id가 null")
		void toEntityNewIssuedCoupon() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.create(10L, 100L);

			// Act
			IssuedCouponEntity entity = mapper.toEntity(issuedCoupon);

			// Assert
			assertAll(
				() -> assertThat(entity.getId()).isNull(),
				() -> assertThat(entity.getCouponTemplateId()).isEqualTo(10L),
				() -> assertThat(entity.getUserId()).isEqualTo(100L),
				() -> assertThat(entity.getStatus()).isEqualTo(IssuedCouponStatus.AVAILABLE)
			);
		}

	}


	@Nested
	@DisplayName("toDomain()")
	class ToDomainTest {

		@Test
		@DisplayName("[toDomain()] IssuedCouponEntity -> IssuedCoupon 변환. 모든 필드가 일치한다")
		void toDomainSuccess() {
			// Arrange
			IssuedCouponEntity entity = IssuedCouponEntity.of(
				1L, 10L, 100L, IssuedCouponStatus.AVAILABLE
			);

			// Act
			IssuedCoupon issuedCoupon = mapper.toDomain(entity);

			// Assert
			assertAll(
				() -> assertThat(issuedCoupon.getId()).isEqualTo(1L),
				() -> assertThat(issuedCoupon.getCouponTemplateId()).isEqualTo(10L),
				() -> assertThat(issuedCoupon.getUserId()).isEqualTo(100L),
				() -> assertThat(issuedCoupon.getStatus()).isEqualTo(IssuedCouponStatus.AVAILABLE),
				() -> assertThat(issuedCoupon.getCreatedAt()).isNull()
			);
		}

	}

}
