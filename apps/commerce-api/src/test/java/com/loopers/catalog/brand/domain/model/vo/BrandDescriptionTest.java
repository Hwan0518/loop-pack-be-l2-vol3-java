package com.loopers.catalog.brand.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


@DisplayName("BrandDescription 값 객체 테스트")
class BrandDescriptionTest {

	@Nested
	@DisplayName("create() 테스트")
	class CreateTest {

		@Test
		@DisplayName("[BrandDescription.create()] 유효한 설명 -> BrandDescription 생성")
		void createSuccess() {
			// Act
			BrandDescription description = BrandDescription.create("스포츠 브랜드");

			// Assert
			assertThat(description.value()).isEqualTo("스포츠 브랜드");
		}


		@Test
		@DisplayName("[BrandDescription.create()] 앞뒤 공백 포함 -> trim 처리 후 BrandDescription 생성")
		void createWithTrim() {
			// Act
			BrandDescription description = BrandDescription.create("  스포츠 브랜드  ");

			// Assert
			assertThat(description.value()).isEqualTo("스포츠 브랜드");
		}


		@Test
		@DisplayName("[BrandDescription.create()] null -> null 반환")
		void createWithNull() {
			// Act
			BrandDescription description = BrandDescription.create(null);

			// Assert
			assertThat(description).isNull();
		}


		@Test
		@DisplayName("[BrandDescription.create()] 500자 초과 -> INVALID_BRAND_DESCRIPTION 예외")
		void createFailWhenExceedsMaxLength() {
			// Arrange
			String longDescription = "가".repeat(501);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> BrandDescription.create(longDescription));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_BRAND_DESCRIPTION),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_BRAND_DESCRIPTION.getMessage())
			);
		}


		@Test
		@DisplayName("[BrandDescription.create()] 정확히 500자 -> BrandDescription 정상 생성")
		void createSuccessWhenExactlyMaxLength() {
			// Arrange
			String exactDescription = "가".repeat(500);

			// Act
			BrandDescription description = BrandDescription.create(exactDescription);

			// Assert
			assertThat(description.value()).isEqualTo(exactDescription);
		}

	}


	@Nested
	@DisplayName("from() 테스트")
	class FromTest {

		@Test
		@DisplayName("[BrandDescription.from()] 유효한 값 -> BrandDescription 복원")
		void fromSuccess() {
			// Act
			BrandDescription description = BrandDescription.from("스포츠 브랜드");

			// Assert
			assertThat(description.value()).isEqualTo("스포츠 브랜드");
		}


		@Test
		@DisplayName("[BrandDescription.from()] null -> null 반환")
		void fromWithNull() {
			// Act
			BrandDescription description = BrandDescription.from(null);

			// Assert
			assertThat(description).isNull();
		}

	}

}
