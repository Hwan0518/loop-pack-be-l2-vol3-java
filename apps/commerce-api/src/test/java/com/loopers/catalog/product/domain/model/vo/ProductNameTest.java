package com.loopers.catalog.product.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;


@DisplayName("ProductName 값 객체 테스트")
class ProductNameTest {

	@Nested
	@DisplayName("create()")
	class CreateTest {

		@Test
		@DisplayName("[create()] 유효한 상품명 -> ProductName 생성 성공")
		void createWithValidName() {
			// Arrange
			String rawName = "테스트 상품";

			// Act
			ProductName productName = ProductName.create(rawName);

			// Assert
			assertThat(productName.value()).isEqualTo("테스트 상품");
		}


		@Test
		@DisplayName("[create()] 앞뒤 공백 포함 상품명 -> trim 처리 후 생성")
		void createWithSpaces() {
			// Arrange
			String rawName = "  테스트 상품  ";

			// Act
			ProductName productName = ProductName.create(rawName);

			// Assert
			assertThat(productName.value()).isEqualTo("테스트 상품");
		}


		@Test
		@DisplayName("[create()] 1자 상품명 -> 생성 성공 (최소 길이)")
		void createWithMinLength() {
			// Act
			ProductName productName = ProductName.create("A");

			// Assert
			assertThat(productName.value()).isEqualTo("A");
		}


		@Test
		@DisplayName("[create()] 200자 상품명 -> 생성 성공 (최대 길이)")
		void createWithMaxLength() {
			// Arrange
			String name = "가".repeat(200);

			// Act
			ProductName productName = ProductName.create(name);

			// Assert
			assertThat(productName.value()).hasSize(200);
		}


		@Test
		@DisplayName("[create()] null -> INVALID_PRODUCT_NAME 예외")
		void createWithNull() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> ProductName.create(null));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PRODUCT_NAME),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PRODUCT_NAME.getMessage())
			);
		}


		@Test
		@DisplayName("[create()] 빈 문자열 -> INVALID_PRODUCT_NAME 예외")
		void createWithEmpty() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> ProductName.create(""));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PRODUCT_NAME);
		}


		@Test
		@DisplayName("[create()] 공백만 포함 -> INVALID_PRODUCT_NAME 예외")
		void createWithBlank() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> ProductName.create("   "));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PRODUCT_NAME);
		}


		@Test
		@DisplayName("[create()] 201자 상품명 -> INVALID_PRODUCT_NAME 예외 (최대 길이 초과)")
		void createWithExceedMaxLength() {
			// Arrange
			String name = "가".repeat(201);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> ProductName.create(name));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PRODUCT_NAME);
		}

	}


	@Nested
	@DisplayName("from()")
	class FromTest {

		@Test
		@DisplayName("[from()] 유효한 값 -> ProductName 생성 (검증 생략)")
		void fromValidValue() {
			// Act
			ProductName productName = ProductName.from("DB 상품명");

			// Assert
			assertThat(productName.value()).isEqualTo("DB 상품명");
		}


		@Test
		@DisplayName("[from()] null -> NullPointerException")
		void fromNull() {
			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> ProductName.from(null));
		}

	}


	@Nested
	@DisplayName("normalize()")
	class NormalizeTest {

		@Test
		@DisplayName("[normalize()] null -> null 반환")
		void normalizeNull() {
			// Act & Assert
			assertThat(ProductName.normalize(null)).isNull();
		}


		@Test
		@DisplayName("[normalize()] 빈 문자열 -> null 반환")
		void normalizeEmpty() {
			// Act & Assert
			assertThat(ProductName.normalize("")).isNull();
		}


		@Test
		@DisplayName("[normalize()] 공백만 -> null 반환")
		void normalizeBlank() {
			// Act & Assert
			assertThat(ProductName.normalize("   ")).isNull();
		}


		@Test
		@DisplayName("[normalize()] 앞뒤 공백 -> trim 처리")
		void normalizeWithSpaces() {
			// Act & Assert
			assertThat(ProductName.normalize("  상품  ")).isEqualTo("상품");
		}

	}

}
