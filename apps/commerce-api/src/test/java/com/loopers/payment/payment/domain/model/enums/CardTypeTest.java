package com.loopers.payment.payment.domain.model.enums;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;


@DisplayName("CardType 테스트")
class CardTypeTest {

	@Test
	@DisplayName("[from()] 'SAMSUNG' 문자열 -> SAMSUNG CardType 반환")
	void fromSamsung() {
		// Act & Assert
		assertThat(CardType.from("SAMSUNG")).isEqualTo(CardType.SAMSUNG);
	}

	@Test
	@DisplayName("[from()] 'KB' 문자열 -> KB CardType 반환")
	void fromKb() {
		// Act & Assert
		assertThat(CardType.from("KB")).isEqualTo(CardType.KB);
	}

	@Test
	@DisplayName("[from()] 'HYUNDAI' 문자열 -> HYUNDAI CardType 반환")
	void fromHyundai() {
		// Act & Assert
		assertThat(CardType.from("HYUNDAI")).isEqualTo(CardType.HYUNDAI);
	}

	@Test
	@DisplayName("[from()] 'UNKNOWN' 문자열 -> INVALID_CARD_TYPE 예외. 지원하지 않는 카드 타입")
	void fromUnknown() {
		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> CardType.from("UNKNOWN"));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_CARD_TYPE),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_CARD_TYPE.getMessage())
		);
	}

	@Test
	@DisplayName("[from()] null 입력 -> INVALID_CARD_TYPE 예외")
	void fromNull() {
		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> CardType.from(null));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_CARD_TYPE),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_CARD_TYPE.getMessage())
		);
	}

	@Test
	@DisplayName("[from()] 빈 문자열 입력 -> INVALID_CARD_TYPE 예외")
	void fromEmpty() {
		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> CardType.from(""));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_CARD_TYPE),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_CARD_TYPE.getMessage())
		);
	}

	@Test
	@DisplayName("[CardType] enum 상수 개수가 3개임을 보장")
	void enumConstantCount() {
		// Assert
		assertThat(CardType.values()).hasSize(3);
	}

}
