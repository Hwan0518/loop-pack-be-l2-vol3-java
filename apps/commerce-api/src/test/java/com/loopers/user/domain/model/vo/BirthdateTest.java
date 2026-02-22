package com.loopers.user.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


@DisplayName("Birthdate 값 객체 테스트")
class BirthdateTest {

	@Nested
	@DisplayName("create 테스트")
	class CreateTest {

		@Test
		@DisplayName("[Birthdate.create()] null -> CoreException(INVALID_BIRTH_DATE). 에러 메시지: '생일을 입력해주세요.'")
		void failWhenNull() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Birthdate.create(null));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_BIRTH_DATE),
				() -> assertThat(exception.getMessage()).isEqualTo("생일을 입력해주세요.")
			);
		}


		@Test
		@DisplayName("[Birthdate.create()] 1899-12-31 -> CoreException(INVALID_BIRTH_DATE). 1900년 1월 1일 이전이므로 거부")
		void failWhenBefore1900() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Birthdate.create(LocalDate.of(1899, 12, 31)));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_BIRTH_DATE),
				() -> assertThat(exception.getMessage()).contains("1900년 1월 1일 이후")
			);
		}


		@Test
		@DisplayName("[Birthdate.create()] 1900-01-01 -> 정상 생성. MIN_BIRTH_DATE 경계값 허용")
		void createWithMinBoundary() {
			// Act
			Birthdate birthdate = Birthdate.create(LocalDate.of(1900, 1, 1));

			// Assert
			assertThat(birthdate.value()).isEqualTo(LocalDate.of(1900, 1, 1));
		}


		@Test
		@DisplayName("[Birthdate.create()] 유효한 과거 날짜 -> 정상 생성")
		void createWithValidDate() {
			// Arrange
			LocalDate validDate = LocalDate.of(1990, 1, 15);

			// Act
			Birthdate birthdate = Birthdate.create(validDate);

			// Assert
			assertThat(birthdate.value()).isEqualTo(validDate);
		}


		@Test
		@DisplayName("[Birthdate.create()] 오늘 날짜 -> CoreException(INVALID_BIRTH_DATE). 오늘 이후 생년월일 불가")
		void failWhenToday() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Birthdate.create(LocalDate.now()));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_BIRTH_DATE),
				() -> assertThat(exception.getMessage()).contains("오늘 이후")
			);
		}


		@Test
		@DisplayName("[Birthdate.create()] 내일 날짜 -> CoreException(INVALID_BIRTH_DATE). 미래 생년월일 불가")
		void failWhenTomorrow() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Birthdate.create(LocalDate.now().plusDays(1)));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_BIRTH_DATE),
				() -> assertThat(exception.getMessage()).contains("오늘 이후")
			);
		}

	}

	@Nested
	@DisplayName("from 테스트")
	class FromTest {

		@Test
		@DisplayName("[Birthdate.from()] DB 복원용 생성 -> 검증 없이 Birthdate 객체 생성")
		void fromWithoutValidation() {
			// Arrange
			LocalDate date = LocalDate.of(1990, 1, 15);

			// Act
			Birthdate birthdate = Birthdate.from(date);

			// Assert
			assertThat(birthdate.value()).isEqualTo(date);
		}

	}

}
