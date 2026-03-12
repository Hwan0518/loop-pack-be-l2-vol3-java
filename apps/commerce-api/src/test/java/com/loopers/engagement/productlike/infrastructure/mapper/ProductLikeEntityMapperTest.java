package com.loopers.engagement.productlike.infrastructure.mapper;


import com.loopers.engagement.productlike.domain.model.ProductLike;
import com.loopers.engagement.productlike.infrastructure.entity.ProductLikeEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@DisplayName("ProductLikeEntityMapper 테스트")
class ProductLikeEntityMapperTest {

	private final ProductLikeEntityMapper mapper = new ProductLikeEntityMapper();


	@Nested
	@DisplayName("toEntity() - 도메인 -> 엔티티 변환")
	class ToEntityTest {

		@Test
		@DisplayName("[toEntity()] ProductLike -> ProductLikeEntity 변환. userId, targetId 매핑 확인")
		void toEntity() {
			// Arrange
			ProductLike productLike = ProductLike.create(1L, 100L);

			// Act
			ProductLikeEntity entity = mapper.toEntity(productLike);

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
			ProductLikeEntity entity = ProductLikeEntity.of(1L, 100L);
			// createdAt은 @PrePersist로 설정되므로, reconstruct를 통해 직접 도메인 생성 후 역변환 검증
			// 대신 Reflection으로 createdAt 설정
			ZonedDateTime now = ZonedDateTime.now();
			setCreatedAt(entity, now);

			// Act
			ProductLike domain = mapper.toDomain(entity);

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
			ProductLikeEntity entity = ProductLikeEntity.of(1L, 100L);
			// createdAt은 기본적으로 null (@PrePersist 전)

			// Act
			ProductLike domain = mapper.toDomain(entity);

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
			ProductLike original = ProductLike.create(1L, 100L);

			// Act
			ProductLikeEntity entity = mapper.toEntity(original);
			ProductLike reconstructed = mapper.toDomain(entity);

			// Assert — userId, targetId 보존 검증 (createdAt은 @PrePersist에서 설정)
			assertAll(
				() -> assertThat(reconstructed.getUserId()).isEqualTo(original.getUserId()),
				() -> assertThat(reconstructed.getTargetId()).isEqualTo(original.getTargetId())
			);
		}
	}


	private void setCreatedAt(ProductLikeEntity entity, ZonedDateTime createdAt) {
		try {
			var field = entity.getClass().getSuperclass().getDeclaredField("createdAt");
			field.setAccessible(true);
			field.set(entity, createdAt);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
