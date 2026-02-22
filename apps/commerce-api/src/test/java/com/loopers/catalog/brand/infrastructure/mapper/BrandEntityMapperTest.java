package com.loopers.catalog.brand.infrastructure.mapper;


import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.domain.model.vo.BrandDescription;
import com.loopers.catalog.brand.domain.model.vo.BrandName;
import com.loopers.catalog.brand.infrastructure.entity.BrandEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@DisplayName("BrandEntityMapper 테스트")
class BrandEntityMapperTest {

	private BrandEntityMapper brandEntityMapper;


	@BeforeEach
	void setUp() {
		brandEntityMapper = new BrandEntityMapper();
	}


	@Nested
	@DisplayName("toEntity() 테스트")
	class ToEntityTest {

		@Test
		@DisplayName("[BrandEntityMapper.toEntity()] id가 null인 Brand -> id가 null인 BrandEntity 변환")
		void toEntityWithoutId() {
			// Arrange
			Brand brand = Brand.create(BrandName.create("나이키"), BrandDescription.create("스포츠 브랜드"));

			// Act
			BrandEntity entity = brandEntityMapper.toEntity(brand);

			// Assert
			assertAll(
				() -> assertThat(entity.getId()).isNull(),
				() -> assertThat(entity.getName()).isEqualTo("나이키"),
				() -> assertThat(entity.getDescription()).isEqualTo("스포츠 브랜드"),
				() -> assertThat(entity.getVisibleStatus()).isEqualTo(VisibleStatus.HIDDEN),
				() -> assertThat(entity.getDeletedAt()).isNull()
			);
		}


		@Test
		@DisplayName("[BrandEntityMapper.toEntity()] id가 있는 Brand -> id가 포함된 BrandEntity 변환")
		void toEntityWithId() {
			// Arrange
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.VISIBLE, null);

			// Act
			BrandEntity entity = brandEntityMapper.toEntity(brand);

			// Assert
			assertAll(
				() -> assertThat(entity.getId()).isEqualTo(1L),
				() -> assertThat(entity.getName()).isEqualTo("나이키"),
				() -> assertThat(entity.getDescription()).isEqualTo("스포츠 브랜드"),
				() -> assertThat(entity.getVisibleStatus()).isEqualTo(VisibleStatus.VISIBLE)
			);
		}


		@Test
		@DisplayName("[BrandEntityMapper.toEntity()] deletedAt이 non-null인 Brand -> soft delete된 BrandEntity 변환")
		void toEntityWithDeletedAt() {
			// Arrange
			ZonedDateTime deletedAt = ZonedDateTime.now();
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, deletedAt);

			// Act
			BrandEntity entity = brandEntityMapper.toEntity(brand);

			// Assert
			assertThat(entity.getDeletedAt()).isNotNull();
		}


		@Test
		@DisplayName("[BrandEntityMapper.toEntity()] visibleStatus 양방향 매핑 -> HIDDEN 정상 변환")
		void toEntityWithHiddenStatus() {
			// Arrange
			Brand brand = Brand.create(BrandName.create("나이키"), BrandDescription.create("스포츠 브랜드"));

			// Act
			BrandEntity entity = brandEntityMapper.toEntity(brand);

			// Assert
			assertThat(entity.getVisibleStatus()).isEqualTo(VisibleStatus.HIDDEN);
		}

	}


	@Nested
	@DisplayName("toDomain() 테스트")
	class ToDomainTest {

		@Test
		@DisplayName("[BrandEntityMapper.toDomain()] BrandEntity -> Brand 도메인 모델 변환")
		void toDomainSuccess() {
			// Arrange
			BrandEntity entity = BrandEntity.of(1L, "나이키", "스포츠 브랜드", VisibleStatus.VISIBLE);

			// Act
			Brand brand = brandEntityMapper.toDomain(entity);

			// Assert
			assertAll(
				() -> assertThat(brand.getId()).isEqualTo(1L),
				() -> assertThat(brand.getName().value()).isEqualTo("나이키"),
				() -> assertThat(brand.getDescription().value()).isEqualTo("스포츠 브랜드"),
				() -> assertThat(brand.getVisibleStatus()).isEqualTo(VisibleStatus.VISIBLE),
				() -> assertThat(brand.getDeletedAt()).isNull()
			);
		}


		@Test
		@DisplayName("[BrandEntityMapper.toDomain()] description이 null인 BrandEntity -> description=null Brand 반환")
		void toDomainWithNullDescription() {
			// Arrange
			BrandEntity entity = BrandEntity.of(1L, "나이키", null, VisibleStatus.HIDDEN);

			// Act
			Brand brand = brandEntityMapper.toDomain(entity);

			// Assert
			assertAll(
				() -> assertThat(brand.getName().value()).isEqualTo("나이키"),
				() -> assertThat(brand.getDescription()).isNull(),
				() -> assertThat(brand.getVisibleStatus()).isEqualTo(VisibleStatus.HIDDEN)
			);
		}

	}

}
