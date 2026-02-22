package com.loopers.catalog.brand.infrastructure.repository;


import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.domain.model.vo.BrandDescription;
import com.loopers.catalog.brand.domain.model.vo.BrandName;
import com.loopers.catalog.brand.domain.repository.BrandCommandRepository;
import com.loopers.catalog.brand.domain.repository.BrandQueryRepository;
import com.loopers.catalog.brand.domain.repository.vo.PageCriteria;
import com.loopers.catalog.brand.domain.repository.vo.PageResult;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("BrandQueryRepository 통합 테스트")
class BrandQueryRepositoryTest {

	@Autowired
	private BrandQueryRepository brandQueryRepository;

	@Autowired
	private BrandCommandRepository brandCommandRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Nested
	@DisplayName("findById() 테스트")
	class FindByIdTest {

		@Test
		@DisplayName("[BrandQueryRepository.findById()] 존재하는 ID 조회 -> Brand 반환")
		void findByIdSuccess() {
			// Arrange
			Brand brand = Brand.create(BrandName.create("나이키"), BrandDescription.create("스포츠 브랜드"));
			Brand savedBrand = brandCommandRepository.save(brand);

			// Act
			Optional<Brand> found = brandQueryRepository.findById(savedBrand.getId());

			// Assert
			assertAll(
				() -> assertThat(found).isPresent(),
				() -> assertThat(found.get().getName().value()).isEqualTo("나이키"),
				() -> assertThat(found.get().getDescription().value()).isEqualTo("스포츠 브랜드")
			);
		}


		@Test
		@DisplayName("[BrandQueryRepository.findById()] 존재하지 않는 ID 조회 -> Optional.empty() 반환")
		void findByIdNotFound() {
			// Act
			Optional<Brand> found = brandQueryRepository.findById(999L);

			// Assert
			assertThat(found).isEmpty();
		}


		@Test
		@DisplayName("[BrandQueryRepository.findById()] soft delete된 Brand ID 조회 -> Optional.empty() 반환")
		void findByIdSoftDeleted() {
			// Arrange
			Brand brand = Brand.create(BrandName.create("나이키"), BrandDescription.create("스포츠 브랜드"));
			Brand savedBrand = brandCommandRepository.save(brand);
			brandCommandRepository.delete(savedBrand);

			// Act
			Optional<Brand> found = brandQueryRepository.findById(savedBrand.getId());

			// Assert
			assertThat(found).isEmpty();
		}

	}


	@Nested
	@DisplayName("findAll() 테스트")
	class FindAllTest {

		@Test
		@DisplayName("[BrandQueryRepository.findAll()] 브랜드 없음 -> 빈 목록 반환")
		void findAllEmpty() {
			// Act
			PageResult<Brand> result = brandQueryRepository.findAll(new PageCriteria(0, 10));

			// Assert
			assertAll(
				() -> assertThat(result.content()).isEmpty(),
				() -> assertThat(result.totalElements()).isZero()
			);
		}


		@Test
		@DisplayName("[BrandQueryRepository.findAll()] 3개 브랜드 저장, 페이지 크기 2 -> 2개 반환, totalElements=3")
		void findAllWithPagination() {
			// Arrange
			brandCommandRepository.save(Brand.create(BrandName.create("나이키"), BrandDescription.create("스포츠 브랜드")));
			brandCommandRepository.save(Brand.create(BrandName.create("아디다스"), BrandDescription.create("독일 스포츠 브랜드")));
			brandCommandRepository.save(Brand.create(BrandName.create("뉴발란스"), BrandDescription.create("미국 스포츠 브랜드")));

			// Act
			PageResult<Brand> result = brandQueryRepository.findAll(new PageCriteria(0, 2));

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(2),
				() -> assertThat(result.totalElements()).isEqualTo(3),
				() -> assertThat(result.page()).isZero(),
				() -> assertThat(result.size()).isEqualTo(2)
			);
		}


		@Test
		@DisplayName("[BrandQueryRepository.findAll()] soft delete된 Brand -> 목록에서 제외")
		void findAllExcludesSoftDeleted() {
			// Arrange
			Brand brand1 = brandCommandRepository.save(Brand.create(BrandName.create("나이키"), BrandDescription.create("스포츠 브랜드")));
			brandCommandRepository.save(Brand.create(BrandName.create("아디다스"), BrandDescription.create("독일 스포츠 브랜드")));
			brandCommandRepository.delete(brand1);

			// Act
			PageResult<Brand> result = brandQueryRepository.findAll(new PageCriteria(0, 10));

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.totalElements()).isEqualTo(1),
				() -> assertThat(result.content().get(0).getName().value()).isEqualTo("아디다스")
			);
		}


		@Test
		@DisplayName("[BrandQueryRepository.findAll()] ID 내림차순 정렬 -> 최신 브랜드 우선 반환")
		void findAllOrderByIdDesc() {
			// Arrange
			brandCommandRepository.save(Brand.create(BrandName.create("나이키"), null));
			brandCommandRepository.save(Brand.create(BrandName.create("아디다스"), null));
			brandCommandRepository.save(Brand.create(BrandName.create("뉴발란스"), null));

			// Act
			PageResult<Brand> result = brandQueryRepository.findAll(new PageCriteria(0, 10));

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(3),
				() -> assertThat(result.content().get(0).getName().value()).isEqualTo("뉴발란스"),
				() -> assertThat(result.content().get(1).getName().value()).isEqualTo("아디다스"),
				() -> assertThat(result.content().get(2).getName().value()).isEqualTo("나이키")
			);
		}

	}


	@Nested
	@DisplayName("findVisibleById() 테스트")
	class FindVisibleByIdTest {

		@Test
		@DisplayName("[BrandQueryRepository.findVisibleById()] VISIBLE 브랜드 조회 -> Brand 반환")
		void findVisibleByIdSuccess() {
			// Arrange
			Brand brand = Brand.create(BrandName.create("나이키"), BrandDescription.create("스포츠 브랜드"));
			Brand savedBrand = brandCommandRepository.save(brand);

			// visibleStatus를 VISIBLE로 변경
			savedBrand.changeVisibleStatus(VisibleStatus.VISIBLE);
			Brand visibleBrand = brandCommandRepository.save(savedBrand);

			// Act
			Optional<Brand> found = brandQueryRepository.findVisibleById(visibleBrand.getId());

			// Assert
			assertAll(
				() -> assertThat(found).isPresent(),
				() -> assertThat(found.get().getName().value()).isEqualTo("나이키"),
				() -> assertThat(found.get().getVisibleStatus()).isEqualTo(VisibleStatus.VISIBLE)
			);
		}


		@Test
		@DisplayName("[BrandQueryRepository.findVisibleById()] HIDDEN 브랜드 조회 -> Optional.empty() 반환")
		void findVisibleByIdHidden() {
			// Arrange
			Brand brand = Brand.create(BrandName.create("나이키"), BrandDescription.create("스포츠 브랜드"));
			Brand savedBrand = brandCommandRepository.save(brand);

			// Act
			Optional<Brand> found = brandQueryRepository.findVisibleById(savedBrand.getId());

			// Assert
			assertThat(found).isEmpty();
		}


		@Test
		@DisplayName("[BrandQueryRepository.findVisibleById()] soft delete된 VISIBLE 브랜드 -> Optional.empty() 반환")
		void findVisibleByIdSoftDeleted() {
			// Arrange
			Brand brand = Brand.create(BrandName.create("나이키"), BrandDescription.create("스포츠 브랜드"));
			Brand savedBrand = brandCommandRepository.save(brand);
			savedBrand.changeVisibleStatus(VisibleStatus.VISIBLE);
			Brand visibleBrand = brandCommandRepository.save(savedBrand);
			brandCommandRepository.delete(visibleBrand);

			// Act
			Optional<Brand> found = brandQueryRepository.findVisibleById(visibleBrand.getId());

			// Assert
			assertThat(found).isEmpty();
		}

	}


	@Nested
	@DisplayName("findAllVisible() 테스트")
	class FindAllVisibleTest {

		@Test
		@DisplayName("[BrandQueryRepository.findAllVisible()] VISIBLE 브랜드만 반환. HIDDEN 제외")
		void findAllVisibleFiltersHidden() {
			// Arrange
			Brand hidden = brandCommandRepository.save(Brand.create(BrandName.create("나이키"), BrandDescription.create("스포츠 브랜드")));
			Brand visible = brandCommandRepository.save(Brand.create(BrandName.create("아디다스"), BrandDescription.create("독일 스포츠 브랜드")));
			visible.changeVisibleStatus(VisibleStatus.VISIBLE);
			brandCommandRepository.save(visible);

			// Act
			PageResult<Brand> result = brandQueryRepository.findAllVisible(new PageCriteria(0, 10));

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.totalElements()).isEqualTo(1),
				() -> assertThat(result.content().get(0).getName().value()).isEqualTo("아디다스")
			);
		}


		@Test
		@DisplayName("[BrandQueryRepository.findAllVisible()] VISIBLE 브랜드 없음 -> 빈 목록 반환")
		void findAllVisibleEmpty() {
			// Arrange
			brandCommandRepository.save(Brand.create(BrandName.create("나이키"), BrandDescription.create("스포츠 브랜드")));

			// Act
			PageResult<Brand> result = brandQueryRepository.findAllVisible(new PageCriteria(0, 10));

			// Assert
			assertAll(
				() -> assertThat(result.content()).isEmpty(),
				() -> assertThat(result.totalElements()).isZero()
			);
		}

	}


	@Nested
	@DisplayName("findAllByVisibleStatus() 테스트")
	class FindAllByVisibleStatusTest {

		@Test
		@DisplayName("[BrandQueryRepository.findAllByVisibleStatus()] HIDDEN 필터 -> HIDDEN 브랜드만 반환")
		void findAllByVisibleStatusHidden() {
			// Arrange
			brandCommandRepository.save(Brand.create(BrandName.create("나이키"), BrandDescription.create("스포츠 브랜드")));
			Brand visible = brandCommandRepository.save(Brand.create(BrandName.create("아디다스"), BrandDescription.create("독일 스포츠 브랜드")));
			visible.changeVisibleStatus(VisibleStatus.VISIBLE);
			brandCommandRepository.save(visible);

			// Act
			PageResult<Brand> result = brandQueryRepository.findAllByVisibleStatus(
				VisibleStatus.HIDDEN, new PageCriteria(0, 10));

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.totalElements()).isEqualTo(1),
				() -> assertThat(result.content().get(0).getName().value()).isEqualTo("나이키")
			);
		}


		@Test
		@DisplayName("[BrandQueryRepository.findAllByVisibleStatus()] VISIBLE 필터 -> VISIBLE 브랜드만 반환")
		void findAllByVisibleStatusVisible() {
			// Arrange
			brandCommandRepository.save(Brand.create(BrandName.create("나이키"), BrandDescription.create("스포츠 브랜드")));
			Brand visible = brandCommandRepository.save(Brand.create(BrandName.create("아디다스"), BrandDescription.create("독일 스포츠 브랜드")));
			visible.changeVisibleStatus(VisibleStatus.VISIBLE);
			brandCommandRepository.save(visible);

			// Act
			PageResult<Brand> result = brandQueryRepository.findAllByVisibleStatus(
				VisibleStatus.VISIBLE, new PageCriteria(0, 10));

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.totalElements()).isEqualTo(1),
				() -> assertThat(result.content().get(0).getName().value()).isEqualTo("아디다스")
			);
		}

	}

}
