package com.loopers.ordering.order.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


@DisplayName("SnapshotPrice 값 객체 테스트")
class SnapshotPriceTest {

	@Nested
	@DisplayName("create() 테스트")
	class CreateTest {

		@Test
		@DisplayName("[SnapshotPrice.create()] 유효한 가격 -> SnapshotPrice 생성")
		void createSuccess() {
			// Act
			SnapshotPrice snapshotPrice = SnapshotPrice.create(new BigDecimal("10000"));

			// Assert
			assertThat(snapshotPrice.value()).isEqualByComparingTo(new BigDecimal("10000"));
		}


		@Test
		@DisplayName("[SnapshotPrice.create()] 0원 -> SnapshotPrice 정상 생성")
		void createSuccessWhenZero() {
			// Act
			SnapshotPrice snapshotPrice = SnapshotPrice.create(BigDecimal.ZERO);

			// Assert
			assertThat(snapshotPrice.value()).isEqualByComparingTo(BigDecimal.ZERO);
		}


		@Test
		@DisplayName("[SnapshotPrice.create()] null -> INVALID_SNAPSHOT_PRICE 예외")
		void createFailWhenNull() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> SnapshotPrice.create(null));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_SNAPSHOT_PRICE),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_SNAPSHOT_PRICE.getMessage())
			);
		}


		@Test
		@DisplayName("[SnapshotPrice.create()] 음수 가격 -> INVALID_SNAPSHOT_PRICE 예외")
		void createFailWhenNegative() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> SnapshotPrice.create(new BigDecimal("-1")));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_SNAPSHOT_PRICE),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_SNAPSHOT_PRICE.getMessage())
			);
		}

	}


	@Nested
	@DisplayName("from() 테스트")
	class FromTest {

		@Test
		@DisplayName("[SnapshotPrice.from()] 유효한 값 -> SnapshotPrice 복원")
		void fromSuccess() {
			// Act
			SnapshotPrice snapshotPrice = SnapshotPrice.from(new BigDecimal("50000"));

			// Assert
			assertThat(snapshotPrice.value()).isEqualByComparingTo(new BigDecimal("50000"));
		}


		@Test
		@DisplayName("[SnapshotPrice.from()] null -> NullPointerException 예외")
		void fromFailWhenNull() {
			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> SnapshotPrice.from(null));
		}

	}

}
