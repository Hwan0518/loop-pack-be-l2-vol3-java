package com.loopers.user.application.facade;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.out.UserMeOutDto;
import com.loopers.user.application.service.UserCommandService;
import com.loopers.user.domain.model.User;
import com.loopers.user.domain.model.vo.LoginId;
import com.loopers.user.domain.model.vo.Password;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("UserQueryFacade 테스트")
class UserQueryFacadeTest {

	private static final String VALID_LOGIN_ID = "testuser01";
	private static final String VALID_PASSWORD = "Test1234!";
	private static final String VALID_NAME = "홍길동";
	private static final LocalDate VALID_BIRTHDAY = LocalDate.of(1990, 1, 15);
	private static final String VALID_EMAIL = "test@example.com";

	@Mock
	private UserCommandService userCommandService;

	private UserQueryFacade userQueryFacade;


	@BeforeEach
	void setUp() {
		userQueryFacade = new UserQueryFacade(userCommandService);
	}


	private User createValidUser() {
		Password password = Password.from("encodedPassword");
		return User.reconstruct(
			1L,
			LoginId.create(VALID_LOGIN_ID),
			password.value(),
			VALID_NAME,
			VALID_BIRTHDAY,
			VALID_EMAIL
		);
	}


	@Nested
	@DisplayName("내 정보 조회 테스트")
	class GetMeTest {

		@Test
		@DisplayName("[UserQueryFacade.getMe()] 유효 헤더 -> UserMeOutDto 반환")
		void getMeSuccess() {
			// Arrange
			User user = createValidUser();
			given(userCommandService.authenticate(VALID_LOGIN_ID, VALID_PASSWORD)).willReturn(user);

			// Act
			UserMeOutDto result = userQueryFacade.getMe(VALID_LOGIN_ID, VALID_PASSWORD);

			// Assert
			assertAll(
				() -> assertThat(result.loginId()).isEqualTo(VALID_LOGIN_ID),
				() -> assertThat(result.name()).isEqualTo(VALID_NAME),
				() -> assertThat(result.birthday()).isEqualTo(VALID_BIRTHDAY),
				() -> assertThat(result.email()).isEqualTo(VALID_EMAIL)
			);
			verify(userCommandService).authenticate(VALID_LOGIN_ID, VALID_PASSWORD);
		}


		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = { "  ", "\t" })
		@DisplayName("[UserQueryFacade.getMe()] loginId null/blank -> UNAUTHORIZED")
		void getMeFailWhenLoginIdNullOrBlank(String loginId) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userQueryFacade.getMe(loginId, VALID_PASSWORD));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
			verify(userCommandService, never()).authenticate(loginId, VALID_PASSWORD);
		}


		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = { "  ", "\t" })
		@DisplayName("[UserQueryFacade.getMe()] password null/blank -> UNAUTHORIZED")
		void getMeFailWhenPasswordNullOrBlank(String password) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userQueryFacade.getMe(VALID_LOGIN_ID, password));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
			verify(userCommandService, never()).authenticate(VALID_LOGIN_ID, password);
		}


		@Test
		@DisplayName("[UserQueryFacade.getMe()] 미존재 사용자 -> USER_NOT_FOUND")
		void getMeFailWhenUserNotFound() {
			// Arrange
			willThrow(new CoreException(ErrorType.USER_NOT_FOUND))
				.given(userCommandService).authenticate("nonexistent", VALID_PASSWORD);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userQueryFacade.getMe("nonexistent", VALID_PASSWORD));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.USER_NOT_FOUND.getMessage())
			);
			verify(userCommandService).authenticate("nonexistent", VALID_PASSWORD);
		}


		@Test
		@DisplayName("[UserQueryFacade.getMe()] 비밀번호 불일치 -> UNAUTHORIZED")
		void getMeFailWhenPasswordNotMatch() {
			// Arrange
			willThrow(new CoreException(ErrorType.UNAUTHORIZED))
				.given(userCommandService).authenticate(VALID_LOGIN_ID, "WrongPass1!");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userQueryFacade.getMe(VALID_LOGIN_ID, "WrongPass1!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
			verify(userCommandService).authenticate(VALID_LOGIN_ID, "WrongPass1!");
		}

	}

}
