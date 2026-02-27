package com.loopers.user.user.application.facade;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.user.application.dto.in.UserChangePasswordInDto;
import com.loopers.user.user.application.dto.in.UserSignUpInDto;
import com.loopers.user.user.application.dto.out.UserSignUpOutDto;
import com.loopers.user.user.application.service.UserCommandService;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
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
		@DisplayName("[UserCommandFacade.signUp()] 유효한 회원가입 정보 -> UserSignUpOutDto 반환. "
			+ "중복 체크 후 CommandService로 저장")
		void signUpSuccess() {
			// Arrange
			UserSignUpInDto inDto = new UserSignUpInDto(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			willDoNothing().given(userQueryService).loginIdDuplicationCheck("testuser01");
			given(userCommandService.createUser(inDto)).willAnswer(invocation -> {
				return User.reconstruct(
					1L,
					LoginId.from("testuser01"),
					Password.from("encodedPw"),
					Name.from("홍길동"),
					Birthdate.from(LocalDate.of(1990, 1, 15)),
					Email.from("test@example.com"),
					null
				);
			});

			// Act
			UserSignUpOutDto result = userCommandFacade.signUp(inDto);

			// Assert
			assertAll(
				() -> assertThat(result).isNotNull(),
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.loginId()).isEqualTo("testuser01"),
				() -> assertThat(result.name()).isEqualTo("홍길동"),
				() -> assertThat(result.email()).isEqualTo("test@example.com")
			);
			verify(userQueryService).loginIdDuplicationCheck("testuser01");
			verify(userCommandService).createUser(inDto);
		}


		@Test
		@DisplayName("[UserCommandFacade.signUp()] 중복된 로그인 ID -> CoreException(ErrorType.USER_ALREADY_EXISTS) 발생. "
			+ "에러 메시지: '이미 가입된 로그인 ID입니다.'")
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
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandFacade.signUp(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_ALREADY_EXISTS),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.USER_ALREADY_EXISTS.getMessage())
			);
			verify(userQueryService).loginIdDuplicationCheck("existinguser");
			verify(userCommandService, never()).createUser(any(UserSignUpInDto.class));
		}


		@Test
		@DisplayName("[UserCommandFacade.signUp()] 대문자/공백 변형 loginId -> 원문 그대로 QueryService에 전달하여 중복 검증")
		void signUpFailWhenNormalizedLoginIdAlreadyExists() {
			// Arrange
			UserSignUpInDto inDto = new UserSignUpInDto(
				"  TESTUSER01  ",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);
			willThrow(new CoreException(ErrorType.USER_ALREADY_EXISTS))
				.given(userQueryService).loginIdDuplicationCheck("  TESTUSER01  ");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandFacade.signUp(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_ALREADY_EXISTS),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.USER_ALREADY_EXISTS.getMessage())
			);
			verify(userQueryService).loginIdDuplicationCheck("  TESTUSER01  ");
			verify(userCommandService, never()).createUser(any(UserSignUpInDto.class));
		}

	}

	@Nested
	@DisplayName("비밀번호 변경 테스트")
	class ChangePasswordTest {

		@Test
		@DisplayName("[UserCommandFacade.changePassword()] 유효한 비밀번호 변경 요청 -> 정상 완료. "
			+ "QueryService 인증 후 CommandService로 변경 위임")
		void changePasswordSuccess() {
			// Arrange
			User user = User.reconstruct(1L, LoginId.from("testuser01"), Password.from("encodedPw"),
				Name.from("홍길동"), Birthdate.from(LocalDate.of(1990, 1, 15)),
				Email.from("test@example.com"), null);
			UserChangePasswordInDto inDto = new UserChangePasswordInDto(
				"testuser01", "Test1234!", "NewPass1234!");
			given(userQueryService.authenticate("testuser01", "Test1234!")).willReturn(user);
			willDoNothing().given(userCommandService).updatePassword(user, "NewPass1234!");

			// Act & Assert
			assertDoesNotThrow(() -> userCommandFacade.changePassword(inDto));
			verify(userQueryService).authenticate("testuser01", "Test1234!");
			verify(userCommandService).updatePassword(user, "NewPass1234!");
		}


		@Test
		@DisplayName("[UserCommandFacade.changePassword()] loginId 원문은 그대로 QueryService 인증에 전달")
		void changePasswordPassesRawLoginIdToService() {
			// Arrange
			User user = User.reconstruct(1L, LoginId.from("testuser01"), Password.from("encodedPw"),
				Name.from("홍길동"), Birthdate.from(LocalDate.of(1990, 1, 15)),
				Email.from("test@example.com"), null);
			UserChangePasswordInDto inDto = new UserChangePasswordInDto(
				"  TESTUSER01  ", "Test1234!", "NewPass1234!");
			given(userQueryService.authenticate("  TESTUSER01  ", "Test1234!")).willReturn(user);
			willDoNothing().given(userCommandService).updatePassword(user, "NewPass1234!");

			// Act & Assert
			assertDoesNotThrow(() -> userCommandFacade.changePassword(inDto));
			verify(userQueryService).authenticate("  TESTUSER01  ", "Test1234!");
			verify(userCommandService).updatePassword(user, "NewPass1234!");
		}


		@Test
		@DisplayName("[UserCommandFacade.changePassword()] 존재하지 않는 사용자 -> CoreException(AUTHENTICATION_FAILED). "
			+ "QueryService 인증 예외를 그대로 전파")
		void failWhenUserNotFound() {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto(
				"nonexistent", "Test1234!", "NewPass1234!");
			willThrow(new CoreException(ErrorType.AUTHENTICATION_FAILED))
				.given(userQueryService).authenticate("nonexistent", "Test1234!");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandFacade.changePassword(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_FAILED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.AUTHENTICATION_FAILED.getMessage())
			);
			verify(userQueryService).authenticate("nonexistent", "Test1234!");
			verify(userCommandService, never()).updatePassword(any(User.class), any(String.class));
		}


		@Test
		@DisplayName("[UserCommandFacade.changePassword()] 헤더 비밀번호 불일치 -> CoreException(AUTHENTICATION_FAILED). "
			+ "QueryService 인증 예외를 그대로 전파")
		void failWhenHeaderPasswordNotMatch() {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto(
				"testuser01", "WrongPass1!", "NewPass1234!");
			willThrow(new CoreException(ErrorType.AUTHENTICATION_FAILED))
				.given(userQueryService).authenticate("testuser01", "WrongPass1!");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandFacade.changePassword(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_FAILED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.AUTHENTICATION_FAILED.getMessage())
			);
			verify(userQueryService).authenticate("testuser01", "WrongPass1!");
			verify(userCommandService, never()).updatePassword(any(User.class), any(String.class));
		}


		@Test
		@DisplayName("[UserCommandFacade.changePassword()] 현재/새 비밀번호 동일 -> CoreException(PASSWORD_SAME_AS_CURRENT). "
			+ "CommandService 예외를 그대로 전파")
		void failWhenNewPasswordSameAsCurrent() {
			// Arrange
			User user = User.reconstruct(1L, LoginId.from("testuser01"), Password.from("encodedPw"),
				Name.from("홍길동"), Birthdate.from(LocalDate.of(1990, 1, 15)),
				Email.from("test@example.com"), null);
			UserChangePasswordInDto inDto = new UserChangePasswordInDto(
				"testuser01", "Test1234!", "Test1234!");
			given(userQueryService.authenticate("testuser01", "Test1234!")).willReturn(user);
			willThrow(new CoreException(ErrorType.PASSWORD_SAME_AS_CURRENT))
				.given(userCommandService).updatePassword(user, "Test1234!");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandFacade.changePassword(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT.getMessage())
			);
			verify(userQueryService).authenticate("testuser01", "Test1234!");
			verify(userCommandService).updatePassword(user, "Test1234!");
		}

	}

}
