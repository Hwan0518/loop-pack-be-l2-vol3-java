package com.loopers.engagement.brandlike.infrastructure.mapper;


import com.loopers.engagement.brandlike.domain.model.BrandLike;
import com.loopers.engagement.brandlike.infrastructure.entity.BrandLikeEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@DisplayName("BrandLikeEntityMapper 테스트")
class BrandLikeEntityMapperTest {

	private final BrandLikeEntityMapper mapper = new BrandLikeEntityMapper();


	@Nested
	@DisplayName("toEntity() - 도메인 -> 엔티티 변환")
	class ToEntityTest {

		@Test
		@DisplayName("[toEntity()] BrandLike -> BrandLikeEntity 변환. userId, targetId 매핑 확인")
		void toEntity() {
			// Arrange
			BrandLike brandLike = BrandLike.create(1L, 100L);

			// Act
			BrandLikeEntity entity = mapper.toEntity(brandLike);

			// Assert
			assertAll(
				() -> assertThat(entity.getUserId()).isEqualTo(1L),
				() -> assertThat(entity.getTargetId()).isEqualTo(100L)
			);
		}
	}


	@Nested
	@DisplayName("toDomain() - 엔티티 -> 도메인 변환")
	class ToDomainTest {

		@Test
		@DisplayName("[toDomain()] createdAt이 존재하는 엔티티 -> createdAt이 LocalDateTime으로 변환됨")
		void toDomainWithCreatedAt() {
			// Arrange
			BrandLikeEntity entity = BrandLikeEntity.of(1L, 100L);
			ZonedDateTime now = ZonedDateTime.now();
			setCreatedAt(entity, now);

			// Act
			BrandLike domain = mapper.toDomain(entity);

			// Assert
			assertAll(
				() -> assertThat(domain.getUserId()).isEqualTo(1L),
				() -> assertThat(domain.getTargetId()).isEqualTo(100L),
				() -> assertThat(domain.getCreatedAt()).isEqualTo(now.toLocalDateTime())
			);
		}


		@Test
		@DisplayName("[toDomain()] createdAt이 null인 엔티티 -> createdAt이 null")
		void toDomainWithNullCreatedAt() {
			// Arrange
			BrandLikeEntity entity = BrandLikeEntity.of(1L, 100L);

			// Act
			BrandLike domain = mapper.toDomain(entity);

			// Assert
			assertAll(
				() -> assertThat(domain.getUserId()).isEqualTo(1L),
				() -> assertThat(domain.getTargetId()).isEqualTo(100L),
				() -> assertThat(domain.getCreatedAt()).isNull()
			);
		}
	}


	@Nested
	@DisplayName("양방향 변환 일관성 테스트")
	class RoundTripTest {

		@Test
		@DisplayName("[toEntity() → toDomain()] 도메인 → 엔티티 → 도메인 변환 시 비즈니스 필드 보존. "
			+ "userId, targetId 값이 원본과 동일")
		void roundTripPreservesFields() {
			// Arrange
			BrandLike original = BrandLike.create(1L, 100L);

			// Act
			BrandLikeEntity entity = mapper.toEntity(original);
			BrandLike reconstructed = mapper.toDomain(entity);

			// Assert — userId, targetId 보존 검증 (createdAt은 @PrePersist에서 설정)
			assertAll(
				() -> assertThat(reconstructed.getUserId()).isEqualTo(original.getUserId()),
				() -> assertThat(reconstructed.getTargetId()).isEqualTo(original.getTargetId())
			);
		}
	}


	private void setCreatedAt(BrandLikeEntity entity, ZonedDateTime createdAt) {
		try {
			var field = entity.getClass().getSuperclass().getDeclaredField("createdAt");
			field.setAccessible(true);
			field.set(entity, createdAt);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
