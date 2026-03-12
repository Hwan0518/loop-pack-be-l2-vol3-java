package com.loopers.coupon.coupontemplate.infrastructure.mapper;


import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
import com.loopers.coupon.coupontemplate.infrastructure.entity.CouponTemplateEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@DisplayName("CouponTemplateEntityMapper 단위 테스트")
class CouponTemplateEntityMapperTest {

	private CouponTemplateEntityMapper mapper;


	@BeforeEach
	void setUp() {
		mapper = new CouponTemplateEntityMapper();
	}


	@Nested
	@DisplayName("toEntity()")
	class ToEntityTest {

		@Test
		@DisplayName("[toEntity()] 활성 CouponTemplate -> Entity 변환. 모든 필드가 일치하고 deletedAt이 null이다")
		void toEntityActiveTemplate() {
			// Arrange
			LocalDateTime expiredAt = LocalDateTime.now().plusDays(30);
			CouponTemplate template = CouponTemplate.reconstruct(
				1L, "10% 할인", CouponType.RATE, new BigDecimal("10"),
				new BigDecimal("5000"), expiredAt, null
			);

			// Act
			CouponTemplateEntity entity = mapper.toEntity(template);

			// Assert
			assertAll(
				() -> assertThat(entity.getId()).isEqualTo(1L),
				() -> assertThat(entity.getName()).isEqualTo("10% 할인"),
				() -> assertThat(entity.getType()).isEqualTo(CouponType.RATE),
				() -> assertThat(entity.getValue()).isEqualByComparingTo(new BigDecimal("10")),
				() -> assertThat(entity.getMinOrderAmount()).isEqualByComparingTo(new BigDecimal("5000")),
				() -> assertThat(entity.getExpiredAt()).isEqualTo(expiredAt),
				() -> assertThat(entity.getDeletedAt()).isNull()
			);
		}


		@Test
		@DisplayName("[toEntity()] 삭제된 CouponTemplate -> Entity 변환. deletedAt이 null이 아니다")
		void toEntityDeletedTemplate() {
			// Arrange
			LocalDateTime expiredAt = LocalDateTime.now().plusDays(30);
			CouponTemplate template = CouponTemplate.reconstruct(
				2L, "5000원 할인", CouponType.FIXED, new BigDecimal("5000"),
				new BigDecimal("10000"), expiredAt, null
			);
			template.delete();

			// Act
			CouponTemplateEntity entity = mapper.toEntity(template);

			// Assert
			assertAll(
				() -> assertThat(entity.getId()).isEqualTo(2L),
				() -> assertThat(entity.getName()).isEqualTo("5000원 할인"),
				() -> assertThat(entity.getDeletedAt()).isNotNull()
			);
		}

	}


	@Nested
	@DisplayName("toDomain()")
	class ToDomainTest {

		@Test
		@DisplayName("[toDomain()] CouponTemplateEntity -> CouponTemplate 변환. 비즈니스 필드가 모두 일치한다")
		void toDomainSuccess() {
			// Arrange
			LocalDateTime expiredAt = LocalDateTime.now().plusDays(30);
			CouponTemplateEntity entity = CouponTemplateEntity.of(
				1L, "10% 할인", CouponType.RATE, new BigDecimal("10"),
				null, expiredAt
			);

			// Act
			CouponTemplate template = mapper.toDomain(entity);

			// Assert
			assertAll(
				() -> assertThat(template.getId()).isEqualTo(1L),
				() -> assertThat(template.getName()).isEqualTo("10% 할인"),
				() -> assertThat(template.getType()).isEqualTo(CouponType.RATE),
				() -> assertThat(template.getValue()).isEqualByComparingTo(new BigDecimal("10")),
				() -> assertThat(template.getMinOrderAmount()).isNull(),
				() -> assertThat(template.getExpiredAt()).isEqualTo(expiredAt),
				() -> assertThat(template.isDeleted()).isFalse()
			);
		}

	}


	@Nested
	@DisplayName("양방향 변환 일관성 테스트")
	class RoundTripTest {

		@Test
		@DisplayName("[toEntity() → toDomain()] 도메인 → 엔티티 → 도메인 변환 시 비즈니스 필드 보존. "
			+ "name, type, value, minOrderAmount, expiredAt 값이 원본과 동일")
		void roundTripPreservesFields() {
			// Arrange
			LocalDateTime expiredAt = LocalDateTime.now().plusDays(30);
			CouponTemplate original = CouponTemplate.reconstruct(
				1L, "10% 할인", CouponType.RATE, new BigDecimal("10"),
				new BigDecimal("5000"), expiredAt, null
			);

			// Act
			CouponTemplateEntity entity = mapper.toEntity(original);
			CouponTemplate reconstructed = mapper.toDomain(entity);

			// Assert — 원본 도메인과 복원된 도메인의 비즈니스 필드 일치 검증
			assertAll(
				() -> assertThat(reconstructed.getId()).isEqualTo(original.getId()),
				() -> assertThat(reconstructed.getName()).isEqualTo(original.getName()),
				() -> assertThat(reconstructed.getType()).isEqualTo(original.getType()),
				() -> assertThat(reconstructed.getValue()).isEqualByComparingTo(original.getValue()),
				() -> assertThat(reconstructed.getMinOrderAmount()).isEqualByComparingTo(original.getMinOrderAmount()),
				() -> assertThat(reconstructed.getExpiredAt()).isEqualTo(original.getExpiredAt()),
				() -> assertThat(reconstructed.isDeleted()).isFalse()
			);
		}

	}

}
