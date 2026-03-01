package com.loopers.support.common;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


@DisplayName("AdminHeaderValidator 테스트")
class AdminHeaderValidatorTest {

	@Test
	@DisplayName("[AdminHeaderValidator.validate()] 유효한 Admin LDAP 헤더 -> 예외 없이 통과")
	void validateSuccess() {
		// Act & Assert
		assertDoesNotThrow(() -> AdminHeaderValidator.validate("loopers.admin"));
	}


	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"  ", "\t", "invalid.value", "loopers.user"})
	@DisplayName("[AdminHeaderValidator.validate()] LDAP 헤더가 null/blank/잘못된 값 -> CoreException(AUTHENTICATION_FAILED)")
	void validateFailWhenLdapHeaderInvalid(String ldapHeader) {
		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> AdminHeaderValidator.validate(ldapHeader));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_FAILED),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.AUTHENTICATION_FAILED.getMessage())
		);
	}

}
