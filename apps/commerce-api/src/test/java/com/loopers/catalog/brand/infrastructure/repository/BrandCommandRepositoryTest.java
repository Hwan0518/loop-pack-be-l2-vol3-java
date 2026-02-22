package com.loopers.catalog.brand.infrastructure.repository;


import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.domain.model.vo.BrandDescription;
import com.loopers.catalog.brand.domain.model.vo.BrandName;
import com.loopers.catalog.brand.domain.repository.BrandCommandRepository;
import com.loopers.catalog.brand.domain.repository.BrandQueryRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("BrandCommandRepository 통합 테스트")
class BrandCommandRepositoryTest {

	@Autowired
	private BrandCommandRepository brandCommandRepository;

	@Autowired
	private BrandQueryRepository brandQueryRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Test
	@DisplayName("[BrandCommandRepository.save()] 유효한 Brand 저장 -> ID가 할당된 Brand 반환. visibleStatus=HIDDEN")
	void saveBrand() {
		// Arrange
		Brand brand = Brand.create(BrandName.create("나이키"), BrandDescription.create("스포츠 브랜드"));

		// Act
		Brand savedBrand = brandCommandRepository.save(brand);

		// Assert
		assertAll(
			() -> assertThat(savedBrand.getId()).isNotNull(),
			() -> assertThat(savedBrand.getName().value()).isEqualTo("나이키"),
			() -> assertThat(savedBrand.getDescription().value()).isEqualTo("스포츠 브랜드"),
			() -> assertThat(savedBrand.getVisibleStatus()).isEqualTo(VisibleStatus.HIDDEN)
		);
	}


	@Test
	@DisplayName("[BrandCommandRepository.save()] description이 null인 Brand 저장 -> description=null인 Brand 반환")
	void saveBrandWithNullDescription() {
		// Arrange
		Brand brand = Brand.create(BrandName.create("나이키"), null);

		// Act
		Brand savedBrand = brandCommandRepository.save(brand);

		// Assert
		assertAll(
			() -> assertThat(savedBrand.getId()).isNotNull(),
			() -> assertThat(savedBrand.getName().value()).isEqualTo("나이키"),
			() -> assertThat(savedBrand.getDescription()).isNull()
		);
	}


	@Test
	@DisplayName("[BrandCommandRepository.save()] 기존 Brand 수정 -> 변경된 Brand 반환 (merge)")
	void updateBrand() {
		// Arrange
		Brand brand = Brand.create(BrandName.create("나이키"), BrandDescription.create("스포츠 브랜드"));
		Brand savedBrand = brandCommandRepository.save(brand);

		Brand updatedBrand = Brand.reconstruct(
			savedBrand.getId(), BrandName.from("아디다스"),
			BrandDescription.from("독일 스포츠 브랜드"),
			VisibleStatus.VISIBLE, null
		);

		// Act
		Brand result = brandCommandRepository.save(updatedBrand);

		// Assert
		assertAll(
			() -> assertThat(result.getId()).isEqualTo(savedBrand.getId()),
			() -> assertThat(result.getName().value()).isEqualTo("아디다스"),
			() -> assertThat(result.getDescription().value()).isEqualTo("독일 스포츠 브랜드"),
			() -> assertThat(result.getVisibleStatus()).isEqualTo(VisibleStatus.VISIBLE)
		);
	}


	@Test
	@DisplayName("[BrandCommandRepository.delete()] 기존 Brand 삭제 -> soft delete 처리. 조회 불가")
	void deleteBrand() {
		// Arrange
		Brand brand = Brand.create(BrandName.create("나이키"), BrandDescription.create("스포츠 브랜드"));
		Brand savedBrand = brandCommandRepository.save(brand);

		// Act
		brandCommandRepository.delete(savedBrand);

		// Assert
		Optional<Brand> found = brandQueryRepository.findById(savedBrand.getId());
		assertThat(found).isEmpty();
	}

}
