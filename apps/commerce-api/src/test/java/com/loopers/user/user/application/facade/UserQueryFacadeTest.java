package com.loopers.user.user.application.facade;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.user.application.dto.out.UserMeOutDto;
import com.loopers.user.user.application.service.UserQueryService;
import com.loopers.user.user.domain.model.User;
import com.loopers.user.user.domain.model.vo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("UserQueryFacade 테스트")
class UserQueryFacadeTest {

	private static final String VALID_LOGIN_ID = "testuser01";
	private static final String VALID_PASSWORD = "Test1234!";
	private static final String VALID_NAME = "홍길동";
	private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(1990, 1, 15);
	private static final String VALID_EMAIL = "test@example.com";

	@Mock
	private UserQueryService userQueryService;

	private UserQueryFacade userQueryFacade;


	@BeforeEach
	void setUp() {
		userQueryFacade = new UserQueryFacade(userQueryService);
	}


	private User createValidUser() {
		return User.reconstruct(1L, LoginId.from(VALID_LOGIN_ID), Password.from("encodedPw"),
			Name.from(VALID_NAME), Birthdate.from(VALID_BIRTH_DATE), Email.from(VALID_EMAIL), null);
	}


	@Nested
	@DisplayName("내 정보 조회 테스트")
	class GetMeTest {

		@Test
		@DisplayName("[UserQueryFacade.getMe()] 유효한 loginId, password -> UserMeOutDto 반환. 비밀번호 매칭 성공")
		void getMeSuccess() {
			// Arrange
			User user = createValidUser();
			given(userQueryService.authenticate(VALID_LOGIN_ID, VALID_PASSWORD)).willReturn(user);

			// Act
			UserMeOutDto result = userQueryFacade.getMe(VALID_LOGIN_ID, VALID_PASSWORD);

			// Assert
			assertAll(
				() -> assertThat(result).isNotNull(),
				() -> assertThat(result.loginId()).isEqualTo(VALID_LOGIN_ID),
				() -> assertThat(result.name()).isEqualTo(VALID_NAME)
			);
			verify(userQueryService).authenticate(VALID_LOGIN_ID, VALID_PASSWORD);
		}


		@Test
		@DisplayName("[UserQueryFacade.getMe()] loginId 앞뒤 공백 -> 원문 loginId로 authenticate 위임")
		void getMePassesRawLoginIdWithWhitespace() {
			// Arrange
			User user = createValidUser();
			given(userQueryService.authenticate("  " + VALID_LOGIN_ID + "  ", VALID_PASSWORD)).willReturn(user);

			// Act
			UserMeOutDto result = userQueryFacade.getMe("  " + VALID_LOGIN_ID + "  ", VALID_PASSWORD);

			// Assert
			assertThat(result.loginId()).isEqualTo(VALID_LOGIN_ID);
			verify(userQueryService).authenticate("  " + VALID_LOGIN_ID + "  ", VALID_PASSWORD);
		}


		@Test
		@DisplayName("[UserQueryFacade.getMe()] loginId 대문자/공백 포함 -> 원문 loginId로 authenticate 위임")
		void getMePassesRawUppercaseLoginId() {
			// Arrange
			User user = createValidUser();
			given(userQueryService.authenticate("  TESTUSER01  ", VALID_PASSWORD)).willReturn(user);

			// Act
			UserMeOutDto result = userQueryFacade.getMe("  TESTUSER01  ", VALID_PASSWORD);

			// Assert
			assertThat(result.loginId()).isEqualTo(VALID_LOGIN_ID);
			verify(userQueryService).authenticate("  TESTUSER01  ", VALID_PASSWORD);
		}


		@Test
		@DisplayName("[UserQueryFacade.getMe()] 존재하지 않는 loginId -> CoreException(AUTHENTICATION_FAILED). "
			+ "인증 실패: 사용자 미존재")
		void getMeFailWhenUserNotFound() {
			// Arrange
			willThrow(new CoreException(ErrorType.AUTHENTICATION_FAILED))
				.given(userQueryService).authenticate("nonexistent", VALID_PASSWORD);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userQueryFacade.getMe("nonexistent", VALID_PASSWORD));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_FAILED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.AUTHENTICATION_FAILED.getMessage())
			);
			verify(userQueryService).authenticate("nonexistent", VALID_PASSWORD);
		}


		@Test
		@DisplayName("[UserQueryFacade.getMe()] 비밀번호 불일치 -> CoreException(AUTHENTICATION_FAILED). User.authenticate() 위임")
		void getMeFailWhenPasswordNotMatch() {
			// Arrange
			willThrow(new CoreException(ErrorType.AUTHENTICATION_FAILED))
				.given(userQueryService).authenticate(VALID_LOGIN_ID, "WrongPass1!");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userQueryFacade.getMe(VALID_LOGIN_ID, "WrongPass1!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_FAILED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.AUTHENTICATION_FAILED.getMessage())
			);
			verify(userQueryService).authenticate(VALID_LOGIN_ID, "WrongPass1!");
		}

	}

}
