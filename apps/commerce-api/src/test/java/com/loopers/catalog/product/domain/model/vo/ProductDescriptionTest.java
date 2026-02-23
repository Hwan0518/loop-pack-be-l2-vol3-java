package com.loopers.catalog.product.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;


@DisplayName("ProductDescription 값 객체 테스트")
class ProductDescriptionTest {

	@Nested
	@DisplayName("create()")
	class CreateTest {

		@Test
		@DisplayName("[create()] 유효한 설명 -> ProductDescription 생성 성공")
		void createWithValidDescription() {
			// Act
			ProductDescription description = ProductDescription.create("상세 설명입니다");

			// Assert
			assertThat(description.value()).isEqualTo("상세 설명입니다");
		}


		@Test
		@DisplayName("[create()] null -> null 반환 (nullable)")
		void createWithNull() {
			// Act
			ProductDescription description = ProductDescription.create(null);

			// Assert
			assertThat(description).isNull();
		}


		@Test
		@DisplayName("[create()] 빈 문자열 -> null 반환")
		void createWithEmpty() {
			// Act
			ProductDescription description = ProductDescription.create("");

			// Assert
			assertThat(description).isNull();
		}


		@Test
		@DisplayName("[create()] 공백만 포함 -> null 반환")
		void createWithBlank() {
			// Act
			ProductDescription description = ProductDescription.create("   ");

			// Assert
			assertThat(description).isNull();
		}


		@Test
		@DisplayName("[create()] 앞뒤 공백 포함 설명 -> trim 처리 후 생성")
		void createWithSpaces() {
			// Act
			ProductDescription description = ProductDescription.create("  설명  ");

			// Assert
			assertThat(description.value()).isEqualTo("설명");
		}


		@Test
		@DisplayName("[create()] 1000자 설명 -> 생성 성공 (최대 길이)")
		void createWithMaxLength() {
			// Arrange
			String desc = "가".repeat(1000);

			// Act
			ProductDescription description = ProductDescription.create(desc);

			// Assert
			assertThat(description.value()).hasSize(1000);
		}


		@Test
		@DisplayName("[create()] 1001자 설명 -> INVALID_PRODUCT_DESCRIPTION 예외 (최대 길이 초과)")
		void createWithExceedMaxLength() {
			// Arrange
			String desc = "가".repeat(1001);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> ProductDescription.create(desc));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PRODUCT_DESCRIPTION);
		}

	}


	@Nested
	@DisplayName("from()")
	class FromTest {

		@Test
		@DisplayName("[from()] 유효한 값 -> ProductDescription 생성 (검증 생략)")
		void fromValidValue() {
			// Act
			ProductDescription description = ProductDescription.from("DB 설명");

			// Assert
			assertThat(description.value()).isEqualTo("DB 설명");
		}


		@Test
		@DisplayName("[from()] null -> null 반환")
		void fromNull() {
			// Act
			ProductDescription description = ProductDescription.from(null);

			// Assert
			assertThat(description).isNull();
		}

	}

}
