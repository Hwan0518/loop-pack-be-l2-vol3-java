package com.loopers.user.application.facade;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.in.UserChangePasswordInDto;
import com.loopers.user.application.dto.in.UserSignUpInDto;
import com.loopers.user.application.dto.out.UserSignUpOutDto;
import com.loopers.user.application.service.UserCommandService;
import com.loopers.user.application.service.UserQueryService;
import com.loopers.user.domain.model.User;
import com.loopers.user.domain.model.vo.LoginId;
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("UserCommandFacade 테스트")
class UserCommandFacadeTest {

	@Mock
	private UserCommandService userCommandService;

	@Mock
	private UserQueryService userQueryService;

	private UserCommandFacade userCommandFacade;


	@BeforeEach
	void setUp() {
		userCommandFacade = new UserCommandFacade(userCommandService, userQueryService);
	}


	@Nested
	@DisplayName("회원가입 테스트")
	class SignUpTest {

		@Test
		@DisplayName("[UserCommandFacade.signUp()] 정상 회원가입 -> 로그인ID 정규화/중복체크 후 UserSignUpOutDto 반환")
		void signUpSuccess() {
			// Arrange
			UserSignUpInDto inDto = new UserSignUpInDto(
				"  TESTUSER01  ",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);
			LoginId expectedLoginId = LoginId.create("  TESTUSER01  ");
			User savedUser = User.reconstruct(
				1L,
				expectedLoginId,
				"encodedPassword",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			given(userCommandService.createUser(any(LoginId.class), eq(inDto))).willReturn(savedUser);

			// Act
			UserSignUpOutDto result = userCommandFacade.signUp(inDto);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.loginId()).isEqualTo("testuser01"),
				() -> assertThat(result.name()).isEqualTo("홍길동")
			);
			verify(userQueryService).loginIdDuplicationCheck("testuser01");
			verify(userCommandService).createUser(
				argThat(loginId -> loginId.value().equals("testuser01")),
				eq(inDto)
			);
		}


		@Test
		@DisplayName("[UserCommandFacade.signUp()] 중복 로그인ID -> USER_ALREADY_EXISTS")
		void signUpFailWhenLoginIdAlreadyExists() {
			// Arrange
			UserSignUpInDto inDto = new UserSignUpInDto(
				"existinguser",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);
			willThrow(new CoreException(ErrorType.USER_ALREADY_EXISTS))
				.given(userQueryService).loginIdDuplicationCheck("existinguser");

			// Act
			CoreException exception = assertThrows(CoreException.class, () -> userCommandFacade.signUp(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_ALREADY_EXISTS),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.USER_ALREADY_EXISTS.getMessage())
			);
			verify(userQueryService).loginIdDuplicationCheck("existinguser");
			verify(userCommandService, never()).createUser(any(LoginId.class), any(UserSignUpInDto.class));
		}


		@Test
		@DisplayName("[UserCommandFacade.signUp()] 로그인ID 형식 오류 -> INVALID_LOGIN_ID_FORMAT")
		void signUpFailWhenLoginIdFormatInvalid() {
			// Arrange
			UserSignUpInDto inDto = new UserSignUpInDto(
				"invalid_id",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			// Act
			CoreException exception = assertThrows(CoreException.class, () -> userCommandFacade.signUp(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT.getMessage())
			);
			verify(userQueryService, never()).loginIdDuplicationCheck(any());
			verify(userCommandService, never()).createUser(any(LoginId.class), any(UserSignUpInDto.class));
		}

	}


	@Nested
	@DisplayName("비밀번호 변경 테스트")
	class ChangePasswordTest {

		@Test
		@DisplayName("[UserCommandFacade.changePassword()] 유효 입력 -> UserCommandService.updatePassword() 위임")
		void changePasswordSuccess() {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto("testuser01", "Test1234!", "NewPass1234!");

			// Act & Assert
			assertDoesNotThrow(() -> userCommandFacade.changePassword(inDto));
			verify(userCommandService).updatePassword(inDto);
		}


		@Test
		@DisplayName("[UserCommandFacade.changePassword()] loginId null/blank -> UNAUTHORIZED")
		void failWhenLoginIdHeaderIsBlank() {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto("   ", "Test1234!", "NewPass1234!");

			// Act
			CoreException exception = assertThrows(CoreException.class, () -> userCommandFacade.changePassword(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
			verify(userCommandService, never()).updatePassword(any(UserChangePasswordInDto.class));
		}


		@Test
		@DisplayName("[UserCommandFacade.changePassword()] currentPassword null/blank -> UNAUTHORIZED")
		void failWhenPasswordHeaderIsBlank() {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto("testuser01", "   ", "NewPass1234!");

			// Act
			CoreException exception = assertThrows(CoreException.class, () -> userCommandFacade.changePassword(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
			verify(userCommandService, never()).updatePassword(any(UserChangePasswordInDto.class));
		}


		@Test
		@DisplayName("[UserCommandFacade.changePassword()] 서비스 USER_NOT_FOUND 예외 전파")
		void failWhenUserNotFound() {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto("nonexistent", "Test1234!", "NewPass1234!");
			willThrow(new CoreException(ErrorType.USER_NOT_FOUND))
				.given(userCommandService).updatePassword(inDto);

			// Act
			CoreException exception = assertThrows(CoreException.class, () -> userCommandFacade.changePassword(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.USER_NOT_FOUND.getMessage())
			);
			verify(userCommandService).updatePassword(inDto);
		}


		@Test
		@DisplayName("[UserCommandFacade.changePassword()] 서비스 PASSWORD_SAME_AS_CURRENT 예외 전파")
		void failWhenNewPasswordSameAsCurrent() {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto("testuser01", "Test1234!", "Test1234!");
			willThrow(new CoreException(ErrorType.PASSWORD_SAME_AS_CURRENT))
				.given(userCommandService).updatePassword(inDto);

			// Act
			CoreException exception = assertThrows(CoreException.class, () -> userCommandFacade.changePassword(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT.getMessage())
			);
			verify(userCommandService).updatePassword(inDto);
		}

	}

}
