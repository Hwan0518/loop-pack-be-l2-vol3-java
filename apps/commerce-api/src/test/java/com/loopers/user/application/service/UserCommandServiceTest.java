package com.loopers.user.application.service;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.in.UserChangePasswordInDto;
import com.loopers.user.application.dto.in.UserSignUpInDto;
import com.loopers.user.application.port.PasswordEncoder;
import com.loopers.user.application.repository.UserCommandRepository;
import com.loopers.user.application.repository.UserQueryRepository;
import com.loopers.user.domain.model.User;
import com.loopers.user.domain.model.vo.LoginId;
import com.loopers.user.domain.model.vo.Password;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

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
@DisplayName("UserCommandService 테스트")
class UserCommandServiceTest {

	@Mock
	private UserQueryRepository userQueryRepository;

	@Mock
	private UserCommandRepository userCommandRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	private UserCommandService userCommandService;


	@BeforeEach
	void setUp() {
		userCommandService = new UserCommandService(userQueryRepository, userCommandRepository, passwordEncoder);
	}


	private User reconstructUser(String loginId, String encodedPassword) {
		return User.reconstruct(
			1L,
			LoginId.create(loginId),
			encodedPassword,
			"홍길동",
			LocalDate.of(1990, 1, 15),
			"test@example.com"
		);
	}


	@Test
	@DisplayName("[UserCommandService.createUser()] 유효 입력 -> User 저장 후 반환")
	void createUserSuccess() {
		// Arrange
		UserSignUpInDto inDto = new UserSignUpInDto(
			"  TESTUSER01  ",
			"Test1234!",
			"홍길동",
			LocalDate.of(1990, 1, 15),
			"test@example.com"
		);
		LoginId loginId = LoginId.create(inDto.loginId());

		given(passwordEncoder.encode("Test1234!")).willReturn("encodedPassword");
		given(userCommandRepository.save(any(User.class))).willAnswer(invocation -> {
			User savedUser = invocation.getArgument(0);
			return User.reconstruct(
				1L,
				savedUser.getLoginId(),
				savedUser.getPassword().value(),
				savedUser.getName(),
				savedUser.getBirthday(),
				savedUser.getEmail()
			);
		});

		// Act
		User result = userCommandService.createUser(loginId, inDto);

		// Assert
		assertAll(
			() -> assertThat(result.getId()).isEqualTo(1L),
			() -> assertThat(result.getLoginId().value()).isEqualTo("testuser01"),
			() -> assertThat(result.getPassword().value()).isEqualTo("encodedPassword")
		);
		verify(passwordEncoder).encode("Test1234!");
		verify(userCommandRepository).save(argThat(saved ->
			saved.getLoginId().value().equals("testuser01") &&
				saved.getName().equals("홍길동") &&
				saved.getEmail().equals("test@example.com")
		));
	}


	@Nested
	@DisplayName("유저 인증 테스트")
	class AuthenticateTest {

		@Test
		@DisplayName("[UserCommandService.authenticate()] 유효한 loginId/password -> 인증된 User 반환")
		void authenticateSuccess() {
			// Arrange
			User user = reconstructUser("testuser01", "encodedCurrent");
			given(userQueryRepository.findByLoginId("testuser01")).willReturn(Optional.of(user));

			// Act
			User authenticated = userCommandService.authenticate("testuser01", "Test1234!");

			// Assert
			assertThat(authenticated).isEqualTo(user);
			verify(userQueryRepository).findByLoginId("testuser01");
			verify(passwordEncoder).authenticate(argThat(password -> password.value().equals("encodedCurrent")), eq("Test1234!"));
		}


		@Test
		@DisplayName("[UserCommandService.authenticate()] 존재하지 않는 loginId -> USER_NOT_FOUND")
		void failWhenAuthenticateUserNotFound() {
			// Arrange
			given(userQueryRepository.findByLoginId("nonexistent")).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandService.authenticate("nonexistent", "Test1234!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.USER_NOT_FOUND.getMessage())
			);
			verify(userQueryRepository).findByLoginId("nonexistent");
			verify(passwordEncoder, never()).authenticate(any(Password.class), any());
		}


		@Test
		@DisplayName("[UserCommandService.authenticate()] 비밀번호 불일치 -> UNAUTHORIZED")
		void failWhenAuthenticatePasswordNotMatch() {
			// Arrange
			User user = reconstructUser("testuser01", "encodedCurrent");
			given(userQueryRepository.findByLoginId("testuser01")).willReturn(Optional.of(user));
			willThrow(new CoreException(ErrorType.UNAUTHORIZED))
				.given(passwordEncoder).authenticate(any(Password.class), eq("WrongPass1!"));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandService.authenticate("testuser01", "WrongPass1!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
			verify(userQueryRepository).findByLoginId("testuser01");
			verify(passwordEncoder).authenticate(any(Password.class), eq("WrongPass1!"));
		}

	}


	@Nested
	@DisplayName("비밀번호 변경 테스트")
	class UpdatePasswordTest {

		@Test
		@DisplayName("[UserCommandService.updatePassword()] 유효한 입력 -> 비밀번호 변경 후 저장")
		void updatePasswordSuccess() {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto("testuser01", "Test1234!", "NewPass1234!");
			User user = reconstructUser("testuser01", "encodedCurrent");

			given(userQueryRepository.findByLoginId("testuser01")).willReturn(Optional.of(user));
			given(passwordEncoder.encode("NewPass1234!")).willReturn("encodedNew");
			given(userCommandRepository.save(any(User.class))).willReturn(user);

			// Act
			assertDoesNotThrow(() -> userCommandService.updatePassword(inDto));

			// Assert
			assertThat(user.getPassword().value()).isEqualTo("encodedNew");
			verify(passwordEncoder).authenticate(any(Password.class), eq("Test1234!"));
			verify(passwordEncoder).validateNewPassword("Test1234!", "NewPass1234!", LocalDate.of(1990, 1, 15));
			verify(passwordEncoder).encode("NewPass1234!");
			verify(userCommandRepository).save(user);
		}


		@Test
		@DisplayName("[UserCommandService.updatePassword()] 존재하지 않는 loginId -> USER_NOT_FOUND")
		void failWhenUserNotFound() {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto("nonexistent", "Test1234!", "NewPass1234!");
			given(userQueryRepository.findByLoginId("nonexistent")).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class, () -> userCommandService.updatePassword(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.USER_NOT_FOUND.getMessage())
			);
			verify(passwordEncoder, never()).validateNewPassword(any(), any(), any());
			verify(userCommandRepository, never()).save(any(User.class));
		}


		@Test
		@DisplayName("[UserCommandService.updatePassword()] 현재 비밀번호 인증 실패 -> UNAUTHORIZED")
		void failWhenCurrentPasswordNotMatch() {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto("testuser01", "WrongPass1!", "NewPass1234!");
			User user = reconstructUser("testuser01", "encodedCurrent");
			given(userQueryRepository.findByLoginId("testuser01")).willReturn(Optional.of(user));
			willThrow(new CoreException(ErrorType.UNAUTHORIZED))
				.given(passwordEncoder).authenticate(any(Password.class), eq("WrongPass1!"));

			// Act
			CoreException exception = assertThrows(CoreException.class, () -> userCommandService.updatePassword(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
			verify(passwordEncoder, never()).validateNewPassword(any(), any(), any());
			verify(userCommandRepository, never()).save(any(User.class));
		}


		@Test
		@DisplayName("[UserCommandService.updatePassword()] 새 비밀번호가 현재 비밀번호와 동일 -> PASSWORD_SAME_AS_CURRENT")
		void failWhenNewPasswordSameAsCurrent() {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto("testuser01", "Test1234!", "Test1234!");
			User user = reconstructUser("testuser01", "encodedCurrent");
			given(userQueryRepository.findByLoginId("testuser01")).willReturn(Optional.of(user));
			willThrow(new CoreException(ErrorType.PASSWORD_SAME_AS_CURRENT))
				.given(passwordEncoder).validateNewPassword("Test1234!", "Test1234!", LocalDate.of(1990, 1, 15));

			// Act
			CoreException exception = assertThrows(CoreException.class, () -> userCommandService.updatePassword(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT.getMessage())
			);
			verify(passwordEncoder, never()).encode(any());
			verify(userCommandRepository, never()).save(any(User.class));
		}

	}

}
