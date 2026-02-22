package com.loopers.user.application.service;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.in.UserChangePasswordInDto;
import com.loopers.user.application.dto.in.UserSignUpInDto;
import com.loopers.user.application.port.out.util.AuthenticationManager;
import com.loopers.user.application.port.out.util.PasswordEncoder;
import com.loopers.user.domain.repository.UserCommandRepository;
import com.loopers.user.domain.repository.UserQueryRepository;
import com.loopers.user.domain.model.User;
import com.loopers.user.domain.model.vo.*;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.*;
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

	@Mock
	private AuthenticationManager authenticationManager;

	private UserCommandService userCommandService;


	@BeforeEach
	void setUp() {
		userCommandService = new UserCommandService(userQueryRepository, userCommandRepository, passwordEncoder, authenticationManager);
	}


	@Test
	@DisplayName("[UserCommandService.createUser()] 유효한 회원가입 DTO -> User 생성 후 저장. "
		+ "정규화된 loginId와 할당된 ID로 반환")
	void createUserSuccess() {
		// Arrange
		UserSignUpInDto inDto = new UserSignUpInDto(
			"  TESTUSER01  ",
			"Test1234!",
			"홍길동",
			LocalDate.of(1990, 1, 15),
			"test@example.com"
		);

		given(passwordEncoder.encode("Test1234!")).willReturn("encodedPassword");
		given(userCommandRepository.save(any(User.class))).willAnswer(invocation -> {
			User savedUser = invocation.getArgument(0);
			return User.reconstruct(
				1L,
				savedUser.getLoginId(),
				savedUser.getPassword(),
				savedUser.getName(),
				savedUser.getBirthDate(),
				savedUser.getEmail(),
				null
			);
		});

		// Act
		User result = userCommandService.createUser(inDto);

		// Assert
		assertAll(
			() -> assertThat(result).isNotNull(),
			() -> assertThat(result.getId()).isEqualTo(1L),
			() -> assertThat(result.getLoginId().value()).isEqualTo("testuser01")
		);
		verify(passwordEncoder).encode("Test1234!");
		verify(userCommandRepository).save(argThat(savedUser ->
			savedUser.getLoginId().value().equals("testuser01") &&
				savedUser.getName().value().equals("홍길동") &&
				savedUser.getBirthDate().value().equals(LocalDate.of(1990, 1, 15)) &&
				savedUser.getEmail().value().equals("test@example.com")
		));
	}


	@Nested
	@DisplayName("유저 인증 테스트")
	class AuthenticateTest {

		@Test
		@DisplayName("[UserCommandService.authenticate()] 유효한 loginId/password -> 인증된 User 반환. "
			+ "loginId 정규화 후 조회")
		void authenticateSuccess() {
			// Arrange
			User user = User.reconstruct(1L, LoginId.from("testuser01"), Password.from("encodedPw"),
				Name.from("홍길동"), Birthdate.from(LocalDate.of(1990, 1, 15)),
				Email.from("test@example.com"), null);
			given(userQueryRepository.findByLoginId("testuser01")).willReturn(Optional.of(user));
			willDoNothing().given(authenticationManager).authenticate(user, "Test1234!");

			// Act
			User authenticated = userCommandService.authenticate("  TESTUSER01  ", "Test1234!");

			// Assert
			assertThat(authenticated.getLoginId().value()).isEqualTo("testuser01");
			verify(userQueryRepository).findByLoginId("testuser01");
			verify(authenticationManager).authenticate(user, "Test1234!");
		}


		@Test
		@DisplayName("[UserCommandService.authenticate()] 존재하지 않는 loginId -> CoreException(AUTHENTICATION_FAILED)")
		void failWhenAuthenticateUserNotFound() {
			// Arrange
			given(userQueryRepository.findByLoginId("nonexistent")).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandService.authenticate("nonexistent", "Test1234!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_FAILED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.AUTHENTICATION_FAILED.getMessage())
			);
			verify(userQueryRepository).findByLoginId("nonexistent");
		}


		@Test
		@DisplayName("[UserCommandService.authenticate()] 비밀번호 불일치 -> CoreException(AUTHENTICATION_FAILED)")
		void failWhenAuthenticatePasswordNotMatch() {
			// Arrange
			User user = User.reconstruct(1L, LoginId.from("testuser01"), Password.from("encodedPw"),
				Name.from("홍길동"), Birthdate.from(LocalDate.of(1990, 1, 15)),
				Email.from("test@example.com"), null);
			given(userQueryRepository.findByLoginId("testuser01")).willReturn(Optional.of(user));
			willThrow(new CoreException(ErrorType.AUTHENTICATION_FAILED))
				.given(authenticationManager).authenticate(user, "WrongPass1!");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandService.authenticate("testuser01", "WrongPass1!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_FAILED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.AUTHENTICATION_FAILED.getMessage())
			);
			verify(userQueryRepository).findByLoginId("testuser01");
		}

	}

	@Nested
	@DisplayName("비밀번호 변경 테스트")
	class UpdatePasswordTest {

		@Test
		@DisplayName("[UserCommandService.updatePassword()] 유효한 입력 -> loginId 정규화 후 조회/인증/비밀번호 변경/저장")
		void updatePasswordSuccess() {
			// Arrange
			User user = User.reconstruct(1L, LoginId.from("testuser01"), Password.from("encodedPw"),
				Name.from("홍길동"), Birthdate.from(LocalDate.of(1990, 1, 15)),
				Email.from("test@example.com"), null);
			UserChangePasswordInDto inDto = new UserChangePasswordInDto(
				"  TESTUSER01  ", "Test1234!", "NewPass1234!");

			given(userQueryRepository.findByLoginId("testuser01")).willReturn(Optional.of(user));
			willDoNothing().given(authenticationManager).authenticate(user, "Test1234!");
			willDoNothing().given(passwordEncoder).checkPasswordDuplication("encodedPw", "NewPass1234!");
			given(passwordEncoder.encode("NewPass1234!")).willReturn("newEncodedPw");
			given(userCommandRepository.save(any(User.class))).willReturn(user);

			// Act & Assert
			assertDoesNotThrow(() -> userCommandService.updatePassword(inDto));

			verify(userQueryRepository).findByLoginId("testuser01");
			verify(authenticationManager).authenticate(user, "Test1234!");
			verify(passwordEncoder).checkPasswordDuplication("encodedPw", "NewPass1234!");
			verify(passwordEncoder).encode("NewPass1234!");
			verify(userCommandRepository).save(user);
		}


		@Test
		@DisplayName("[UserCommandService.updatePassword()] 존재하지 않는 loginId -> CoreException(AUTHENTICATION_FAILED)")
		void failWhenUserNotFound() {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto(
				"nonexistent", "Test1234!", "NewPass1234!");
			given(userQueryRepository.findByLoginId("nonexistent")).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandService.updatePassword(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_FAILED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.AUTHENTICATION_FAILED.getMessage())
			);
			verify(userQueryRepository).findByLoginId("nonexistent");
			verify(userCommandRepository, never()).save(any(User.class));
		}


		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = { "  ", "\t" })
		@DisplayName("[UserCommandService.updatePassword()] rawLoginId null/blank -> CoreException(AUTHENTICATION_FAILED)")
		void failWhenRawLoginIdIsNullOrBlank(String rawLoginId) {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto(
				rawLoginId, "Test1234!", "NewPass1234!");
			given(userQueryRepository.findByLoginId(null)).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandService.updatePassword(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_FAILED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.AUTHENTICATION_FAILED.getMessage())
			);
			verify(userQueryRepository).findByLoginId(null);
			verify(userCommandRepository, never()).save(any(User.class));
		}


		@Test
		@DisplayName("[UserCommandService.updatePassword()] 헤더 비밀번호 불일치 -> CoreException(AUTHENTICATION_FAILED)")
		void failWhenHeaderPasswordNotMatch() {
			// Arrange
			User user = User.reconstruct(1L, LoginId.from("testuser01"), Password.from("encodedPw"),
				Name.from("홍길동"), Birthdate.from(LocalDate.of(1990, 1, 15)),
				Email.from("test@example.com"), null);
			UserChangePasswordInDto inDto = new UserChangePasswordInDto(
				"testuser01", "WrongPass1!", "NewPass1234!");
			given(userQueryRepository.findByLoginId("testuser01")).willReturn(Optional.of(user));
			willThrow(new CoreException(ErrorType.AUTHENTICATION_FAILED))
				.given(authenticationManager).authenticate(user, "WrongPass1!");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandService.updatePassword(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_FAILED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.AUTHENTICATION_FAILED.getMessage())
			);
			verify(userQueryRepository).findByLoginId("testuser01");
			verify(userCommandRepository, never()).save(any(User.class));
		}


		@Test
		@DisplayName("[UserCommandService.updatePassword()] 새 비밀번호가 현재 비밀번호와 동일 -> CoreException(PASSWORD_SAME_AS_CURRENT)")
		void failWhenNewPasswordSameAsCurrent() {
			// Arrange
			User user = User.reconstruct(1L, LoginId.from("testuser01"), Password.from("encodedPw"),
				Name.from("홍길동"), Birthdate.from(LocalDate.of(1990, 1, 15)),
				Email.from("test@example.com"), null);
			UserChangePasswordInDto inDto = new UserChangePasswordInDto(
				"testuser01", "Test1234!", "Test1234!");
			given(userQueryRepository.findByLoginId("testuser01")).willReturn(Optional.of(user));
			willDoNothing().given(authenticationManager).authenticate(user, "Test1234!");
			willThrow(new CoreException(ErrorType.PASSWORD_SAME_AS_CURRENT))
				.given(passwordEncoder).checkPasswordDuplication("encodedPw", "Test1234!");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandService.updatePassword(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT.getMessage())
			);
			verify(userQueryRepository).findByLoginId("testuser01");
			verify(userCommandRepository, never()).save(any(User.class));
		}

	}

}
