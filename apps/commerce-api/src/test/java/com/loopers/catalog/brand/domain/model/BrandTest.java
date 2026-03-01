package com.loopers.catalog.brand.domain.model;


import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.domain.model.vo.BrandDescription;
import com.loopers.catalog.brand.domain.model.vo.BrandName;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


@DisplayName("Brand 도메인 모델 테스트")
class BrandTest {

	@Nested
	@DisplayName("create() 테스트")
	class CreateTest {

		@Test
		@DisplayName("[Brand.create()] 유효한 BrandName, BrandDescription -> Brand 생성. id=null, visibleStatus=HIDDEN, deletedAt=null")
		void createSuccess() {
			// Arrange
			BrandName name = BrandName.create("나이키");
			BrandDescription description = BrandDescription.create("스포츠 브랜드");

			// Act
			Brand brand = Brand.create(name, description);

			// Assert
			assertAll(
				() -> assertThat(brand.getId()).isNull(),
				() -> assertThat(brand.getName().value()).isEqualTo("나이키"),
				() -> assertThat(brand.getDescription().value()).isEqualTo("스포츠 브랜드"),
				() -> assertThat(brand.getVisibleStatus()).isEqualTo(VisibleStatus.HIDDEN),
				() -> assertThat(brand.getDeletedAt()).isNull()
			);
		}


		@Test
		@DisplayName("[Brand.create()] description이 null -> Brand 생성. description=null")
		void createWithNullDescription() {
			// Act
			Brand brand = Brand.create(BrandName.create("나이키"), null);

			// Assert
			assertAll(
				() -> assertThat(brand.getName().value()).isEqualTo("나이키"),
				() -> assertThat(brand.getDescription()).isNull()
			);
		}


		@Test
		@DisplayName("[Brand.create()] name이 null -> NullPointerException 예외")
		void createFailWhenNameIsNull() {
			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> Brand.create(null, BrandDescription.create("설명")));
		}

	}


	@Nested
	@DisplayName("reconstruct() 테스트")
	class ReconstructTest {

		@Test
		@DisplayName("[Brand.reconstruct()] 유효한 인자 -> Brand 복원. 검증 없이 값 설정")
		void reconstructSuccess() {
			// Act
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.VISIBLE, null);

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
		@DisplayName("[Brand.reconstruct()] deletedAt이 non-null -> soft delete 상태 복원")
		void reconstructWithDeletedAt() {
			// Arrange
			ZonedDateTime deletedAt = ZonedDateTime.now();

			// Act
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, deletedAt);

			// Assert
			assertThat(brand.getDeletedAt()).isEqualTo(deletedAt);
		}


		@Test
		@DisplayName("[Brand.reconstruct()] visibleStatus=HIDDEN -> HIDDEN 상태 복원")
		void reconstructWithHiddenStatus() {
			// Act
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, null);

			// Assert
			assertThat(brand.getVisibleStatus()).isEqualTo(VisibleStatus.HIDDEN);
		}

	}


	@Nested
	@DisplayName("changeName() 테스트")
	class ChangeNameTest {

		@Test
		@DisplayName("[Brand.changeName()] 유효한 BrandName -> 브랜드명 변경")
		void changeNameSuccess() {
			// Arrange
			Brand brand = Brand.create(BrandName.create("나이키"), BrandDescription.create("설명"));

			// Act
			brand.changeName(BrandName.create("아디다스"));

			// Assert
			assertThat(brand.getName().value()).isEqualTo("아디다스");
		}


		@Test
		@DisplayName("[Brand.changeName()] null -> NullPointerException 예외")
		void changeNameFailWhenNull() {
			// Arrange
			Brand brand = Brand.create(BrandName.create("나이키"), BrandDescription.create("설명"));

			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> brand.changeName(null));
		}

	}


	@Nested
	@DisplayName("changeDescription() 테스트")
	class ChangeDescriptionTest {

		@Test
		@DisplayName("[Brand.changeDescription()] 유효한 BrandDescription -> 브랜드 설명 변경")
		void changeDescriptionSuccess() {
			// Arrange
			Brand brand = Brand.create(BrandName.create("나이키"), BrandDescription.create("이전 설명"));

			// Act
			brand.changeDescription(BrandDescription.create("새로운 설명"));

			// Assert
			assertThat(brand.getDescription().value()).isEqualTo("새로운 설명");
		}


		@Test
		@DisplayName("[Brand.changeDescription()] null -> description을 null로 설정")
		void changeDescriptionToNull() {
			// Arrange
			Brand brand = Brand.create(BrandName.create("나이키"), BrandDescription.create("이전 설명"));

			// Act
			brand.changeDescription(null);

			// Assert
			assertThat(brand.getDescription()).isNull();
		}

	}


	@Nested
	@DisplayName("changeVisibleStatus() 테스트")
	class ChangeVisibleStatusTest {

		@Test
		@DisplayName("[Brand.changeVisibleStatus()] VISIBLE -> 노출 상태 VISIBLE로 변경")
		void changeVisibleStatusToVisible() {
			// Arrange
			Brand brand = Brand.create(BrandName.create("나이키"), BrandDescription.create("설명"));

			// Act
			brand.changeVisibleStatus(VisibleStatus.VISIBLE);

			// Assert
			assertThat(brand.getVisibleStatus()).isEqualTo(VisibleStatus.VISIBLE);
		}


		@Test
		@DisplayName("[Brand.changeVisibleStatus()] HIDDEN -> 노출 상태 HIDDEN으로 변경")
		void changeVisibleStatusToHidden() {
			// Arrange
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("설명"), VisibleStatus.VISIBLE, null);

			// Act
			brand.changeVisibleStatus(VisibleStatus.HIDDEN);

			// Assert
			assertThat(brand.getVisibleStatus()).isEqualTo(VisibleStatus.HIDDEN);
		}


		@Test
		@DisplayName("[Brand.changeVisibleStatus()] null -> INVALID_BRAND_VISIBLE_STATUS 예외")
		void changeVisibleStatusFailWhenNull() {
			// Arrange
			Brand brand = Brand.create(BrandName.create("나이키"), BrandDescription.create("설명"));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> brand.changeVisibleStatus(null));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_BRAND_VISIBLE_STATUS),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_BRAND_VISIBLE_STATUS.getMessage())
			);
		}

	}


	@Nested
	@DisplayName("isVisible() 테스트")
	class IsVisibleTest {

		@Test
		@DisplayName("[Brand.isVisible()] visibleStatus=VISIBLE -> true 반환")
		void isVisibleWhenVisible() {
			// Arrange
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("설명"), VisibleStatus.VISIBLE, null);

			// Act & Assert
			assertThat(brand.isVisible()).isTrue();
		}


		@Test
		@DisplayName("[Brand.isVisible()] visibleStatus=HIDDEN -> false 반환")
		void isVisibleWhenHidden() {
			// Arrange
			Brand brand = Brand.create(BrandName.create("나이키"), BrandDescription.create("설명"));

			// Act & Assert
			assertThat(brand.isVisible()).isFalse();
		}

	}


	@Nested
	@DisplayName("validateDeletable() 테스트")
	class ValidateDeletableTest {

		@Test
		@DisplayName("[Brand.validateDeletable()] 활성 상품 없음 (false) -> 예외 없이 통과")
		void validateDeletableSuccess() {
			// Arrange
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, null);

			// Act & Assert
			assertDoesNotThrow(() -> brand.validateDeletable(false));
		}


		@Test
		@DisplayName("[Brand.validateDeletable()] VISIBLE 브랜드 -> BRAND_IS_VISIBLE 예외. 노출 중인 브랜드는 삭제 불가")
		void validateDeletableFailWhenVisible() {
			// Arrange
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.VISIBLE, null);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> brand.validateDeletable(false));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.BRAND_IS_VISIBLE),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.BRAND_IS_VISIBLE.getMessage())
			);
		}


		@Test
		@DisplayName("[Brand.validateDeletable()] 활성 상품 존재 (true) -> BRAND_HAS_ACTIVE_PRODUCTS 예외")
		void validateDeletableFailWhenHasActiveProducts() {
			// Arrange
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, null);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> brand.validateDeletable(true));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.BRAND_HAS_ACTIVE_PRODUCTS),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.BRAND_HAS_ACTIVE_PRODUCTS.getMessage())
			);
		}

	}

}
