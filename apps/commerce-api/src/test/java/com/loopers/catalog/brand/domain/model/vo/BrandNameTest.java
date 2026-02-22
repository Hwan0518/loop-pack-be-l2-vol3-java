package com.loopers.catalog.brand.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


@DisplayName("BrandName 값 객체 테스트")
class BrandNameTest {

	@Nested
	@DisplayName("create() 테스트")
	class CreateTest {

		@Test
		@DisplayName("[BrandName.create()] 유효한 이름 -> BrandName 생성")
		void createSuccess() {
			// Act
			BrandName brandName = BrandName.create("나이키");

			// Assert
			assertThat(brandName.value()).isEqualTo("나이키");
		}


		@Test
		@DisplayName("[BrandName.create()] 앞뒤 공백 포함 -> trim 처리 후 BrandName 생성")
		void createWithTrim() {
			// Act
			BrandName brandName = BrandName.create("  나이키  ");

			// Assert
			assertThat(brandName.value()).isEqualTo("나이키");
		}


		@Test
		@DisplayName("[BrandName.create()] null -> INVALID_BRAND_NAME 예외")
		void createFailWhenNull() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> BrandName.create(null));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_BRAND_NAME),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_BRAND_NAME.getMessage())
			);
		}


		@Test
		@DisplayName("[BrandName.create()] 빈 문자열 -> INVALID_BRAND_NAME 예외")
		void createFailWhenEmpty() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> BrandName.create(""));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_BRAND_NAME),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_BRAND_NAME.getMessage())
			);
		}


		@Test
		@DisplayName("[BrandName.create()] 공백만 포함 -> INVALID_BRAND_NAME 예외")
		void createFailWhenBlank() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> BrandName.create("   "));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_BRAND_NAME),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_BRAND_NAME.getMessage())
			);
		}


		@Test
		@DisplayName("[BrandName.create()] 100자 초과 -> INVALID_BRAND_NAME 예외")
		void createFailWhenExceedsMaxLength() {
			// Arrange
			String longName = "가".repeat(101);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> BrandName.create(longName));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_BRAND_NAME),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_BRAND_NAME.getMessage())
			);
		}


		@Test
		@DisplayName("[BrandName.create()] 정확히 100자 -> BrandName 정상 생성")
		void createSuccessWhenExactlyMaxLength() {
			// Arrange
			String exactName = "가".repeat(100);

			// Act
			BrandName brandName = BrandName.create(exactName);

			// Assert
			assertThat(brandName.value()).isEqualTo(exactName);
		}

	}


	@Nested
	@DisplayName("from() 테스트")
	class FromTest {

		@Test
		@DisplayName("[BrandName.from()] 유효한 값 -> BrandName 복원")
		void fromSuccess() {
			// Act
			BrandName brandName = BrandName.from("나이키");

			// Assert
			assertThat(brandName.value()).isEqualTo("나이키");
		}


		@Test
		@DisplayName("[BrandName.from()] null -> NullPointerException 예외")
		void fromFailWhenNull() {
			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> BrandName.from(null));
		}

	}


	@Nested
	@DisplayName("normalize() 테스트")
	class NormalizeTest {

		@Test
		@DisplayName("[BrandName.normalize()] 앞뒤 공백 -> trim 처리")
		void normalizeTrim() {
			// Act & Assert
			assertThat(BrandName.normalize("  나이키  ")).isEqualTo("나이키");
		}


		@Test
		@DisplayName("[BrandName.normalize()] 공백만 포함 -> null 반환")
		void normalizeBlankToNull() {
			// Act & Assert
			assertThat(BrandName.normalize("   ")).isNull();
		}


		@Test
		@DisplayName("[BrandName.normalize()] null -> null 반환")
		void normalizeNullToNull() {
			// Act & Assert
			assertThat(BrandName.normalize(null)).isNull();
		}

	}

}
