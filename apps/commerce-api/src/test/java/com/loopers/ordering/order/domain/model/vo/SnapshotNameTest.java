package com.loopers.ordering.order.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


@DisplayName("SnapshotName 값 객체 테스트")
class SnapshotNameTest {

	@Nested
	@DisplayName("create() 테스트")
	class CreateTest {

		@Test
		@DisplayName("[SnapshotName.create()] 유효한 상품명 -> SnapshotName 생성")
		void createSuccess() {
			// Act
			SnapshotName snapshotName = SnapshotName.create("나이키 에어맥스");

			// Assert
			assertThat(snapshotName.value()).isEqualTo("나이키 에어맥스");
		}


		@Test
		@DisplayName("[SnapshotName.create()] 앞뒤 공백 포함 -> trim 처리 후 SnapshotName 생성")
		void createWithTrim() {
			// Act
			SnapshotName snapshotName = SnapshotName.create("  나이키 에어맥스  ");

			// Assert
			assertThat(snapshotName.value()).isEqualTo("나이키 에어맥스");
		}


		@Test
		@DisplayName("[SnapshotName.create()] null -> INVALID_SNAPSHOT_NAME 예외")
		void createFailWhenNull() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> SnapshotName.create(null));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_SNAPSHOT_NAME),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_SNAPSHOT_NAME.getMessage())
			);
		}


		@Test
		@DisplayName("[SnapshotName.create()] 빈 문자열 -> INVALID_SNAPSHOT_NAME 예외")
		void createFailWhenEmpty() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> SnapshotName.create(""));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_SNAPSHOT_NAME),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_SNAPSHOT_NAME.getMessage())
			);
		}


		@Test
		@DisplayName("[SnapshotName.create()] 공백만 포함 -> INVALID_SNAPSHOT_NAME 예외")
		void createFailWhenBlank() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> SnapshotName.create("   "));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_SNAPSHOT_NAME),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_SNAPSHOT_NAME.getMessage())
			);
		}


		@Test
		@DisplayName("[SnapshotName.create()] 200자 초과 -> INVALID_SNAPSHOT_NAME 예외")
		void createFailWhenExceedsMaxLength() {
			// Arrange
			String longName = "가".repeat(201);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> SnapshotName.create(longName));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_SNAPSHOT_NAME),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_SNAPSHOT_NAME.getMessage())
			);
		}


		@Test
		@DisplayName("[SnapshotName.create()] 정확히 200자 -> SnapshotName 정상 생성")
		void createSuccessWhenExactlyMaxLength() {
			// Arrange
			String exactName = "가".repeat(200);

			// Act
			SnapshotName snapshotName = SnapshotName.create(exactName);

			// Assert
			assertThat(snapshotName.value()).isEqualTo(exactName);
		}

	}


	@Nested
	@DisplayName("from() 테스트")
	class FromTest {

		@Test
		@DisplayName("[SnapshotName.from()] 유효한 값 -> SnapshotName 복원")
		void fromSuccess() {
			// Act
			SnapshotName snapshotName = SnapshotName.from("나이키 에어맥스");

			// Assert
			assertThat(snapshotName.value()).isEqualTo("나이키 에어맥스");
		}


		@Test
		@DisplayName("[SnapshotName.from()] null -> NullPointerException 예외")
		void fromFailWhenNull() {
			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> SnapshotName.from(null));
		}

	}

}
