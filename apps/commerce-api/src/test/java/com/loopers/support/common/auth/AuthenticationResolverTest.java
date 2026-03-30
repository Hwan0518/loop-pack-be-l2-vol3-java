package com.loopers.support.common.auth;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.user.application.facade.UserQueryFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationResolver 테스트")
class AuthenticationResolverTest {

	@Mock
	private UserQueryFacade userQueryFacade;

	private AuthenticationResolver authenticationResolver;


	@BeforeEach
	void setUp() {
		authenticationResolver = new AuthenticationResolver(userQueryFacade);
	}


	@Nested
	@DisplayName("resolveOptional() - 선택적 인증")
	class ResolveOptionalTest {

		@Test
		@DisplayName("[resolveOptional()] 둘 다 null -> null 반환. 비로그인 허용")
		void bothNull() {
			// Act
			Long result = authenticationResolver.resolveOptional(null, null);

			// Assert
			assertAll(
				() -> assertThat(result).isNull(),
				() -> verify(userQueryFacade, never()).authenticateAndGetUserId(null, null)
			);
		}

		@Test
		@DisplayName("[resolveOptional()] 둘 다 blank -> null 반환. 비로그인 허용")
		void bothBlank() {
			// Act
			Long result = authenticationResolver.resolveOptional("  ", "  ");

			// Assert
			assertThat(result).isNull();
		}

		@Test
		@DisplayName("[resolveOptional()] 유효한 인증 헤더 -> 사용자 ID 반환")
		void validCredentials() {
			// Arrange
			given(userQueryFacade.authenticateAndGetUserId("user1", "pass1")).willReturn(10L);

			// Act
			Long result = authenticationResolver.resolveOptional("user1", "pass1");

			// Assert
			assertThat(result).isEqualTo(10L);
		}

		@Test
		@DisplayName("[resolveOptional()] loginId만 있고 password null -> UNAUTHORIZED 예외")
		void loginIdOnlyThrows() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> authenticationResolver.resolveOptional("user1", null));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_FAILED);
		}

		@Test
		@DisplayName("[resolveOptional()] password만 있고 loginId null -> UNAUTHORIZED 예외")
		void passwordOnlyThrows() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> authenticationResolver.resolveOptional(null, "pass1"));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_FAILED);
		}

	}

}
