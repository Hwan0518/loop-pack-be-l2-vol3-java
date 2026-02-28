package com.loopers.user.user.application.service;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.user.application.port.out.util.AuthenticationManager;
import com.loopers.user.user.domain.model.User;
import com.loopers.user.user.domain.model.vo.*;
import com.loopers.user.user.domain.repository.UserQueryRepository;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("UserQueryService 테스트")
class UserQueryServiceTest {

	@Mock
	private UserQueryRepository userQueryRepository;

	@Mock
	private AuthenticationManager authenticationManager;

	private UserQueryService userQueryService;


	@BeforeEach
	void setUp() {
		userQueryService = new UserQueryService(userQueryRepository, authenticationManager);
	}


	@Test
	@DisplayName("[UserQueryService.loginIdDuplicationCheck()] 존재하지 않는 loginId -> 예외 없이 통과")
	void loginIdDuplicationCheckNotExists() {
		// Arrange
		given(userQueryRepository.existsByLoginId("testuser01")).willReturn(false);

		// Act & Assert
		assertDoesNotThrow(() -> userQueryService.loginIdDuplicationCheck("testuser01"));
		verify(userQueryRepository).existsByLoginId("testuser01");
	}


	@Test
	@DisplayName("[UserQueryService.loginIdDuplicationCheck()] 존재하는 loginId -> CoreException(USER_ALREADY_EXISTS) 발생")
	void loginIdDuplicationCheckExists() {
		// Arrange
		given(userQueryRepository.existsByLoginId("testuser01")).willReturn(true);

		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> userQueryService.loginIdDuplicationCheck("testuser01"));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_ALREADY_EXISTS),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.USER_ALREADY_EXISTS.getMessage())
		);
		verify(userQueryRepository).existsByLoginId("testuser01");
	}


	@Test
	@DisplayName("[UserQueryService.loginIdDuplicationCheck()] 대문자/공백 변형 loginId -> 정규화 후 조회. "
		+ "'  TESTUSER01  ' -> existsByLoginId('testuser01') 호출")
	void loginIdDuplicationCheckNormalizesInput() {
		// Arrange
		given(userQueryRepository.existsByLoginId("testuser01")).willReturn(false);

		// Act & Assert
		assertDoesNotThrow(() -> userQueryService.loginIdDuplicationCheck("  TESTUSER01  "));
		verify(userQueryRepository).existsByLoginId("testuser01");
	}


	@Test
	@DisplayName("[UserQueryService.loginIdDuplicationCheck()] null loginId -> CoreException(INVALID_LOGIN_ID_FORMAT). "
		+ "정규화 결과 null이면 DB 조회 없이 즉시 예외")
	void loginIdDuplicationCheckWithNull() {
		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> userQueryService.loginIdDuplicationCheck(null));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT.getMessage())
		);
	}


	@Test
	@DisplayName("[UserQueryService.loginIdDuplicationCheck()] blank loginId -> CoreException(INVALID_LOGIN_ID_FORMAT). "
		+ "정규화 결과 null이면 DB 조회 없이 즉시 예외")
	void loginIdDuplicationCheckWithBlank() {
		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> userQueryService.loginIdDuplicationCheck("   "));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT.getMessage())
		);
	}


	@Nested
	@DisplayName("유저 인증 테스트")
	class AuthenticateTest {

		@Test
		@DisplayName("[UserQueryService.authenticate()] 유효한 loginId/password -> 인증된 User 반환. "
			+ "loginId 정규화 후 조회")
		void authenticateSuccess() {
			// Arrange
			User user = User.reconstruct(1L, LoginId.from("testuser01"), Password.from("encodedPw"),
				Name.from("홍길동"), Birthdate.from(LocalDate.of(1990, 1, 15)),
				Email.from("test@example.com"), null);
			given(userQueryRepository.findByLoginId("testuser01")).willReturn(Optional.of(user));
			willDoNothing().given(authenticationManager).authenticate(user, "Test1234!");

			// Act
			User authenticated = userQueryService.authenticate("  TESTUSER01  ", "Test1234!");

			// Assert
			assertThat(authenticated.getLoginId().value()).isEqualTo("testuser01");
			verify(userQueryRepository).findByLoginId("testuser01");
			verify(authenticationManager).authenticate(user, "Test1234!");
		}


		@Test
		@DisplayName("[UserQueryService.authenticate()] 존재하지 않는 loginId -> CoreException(AUTHENTICATION_FAILED)")
		void failWhenAuthenticateUserNotFound() {
			// Arrange
			given(userQueryRepository.findByLoginId("nonexistent")).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userQueryService.authenticate("nonexistent", "Test1234!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_FAILED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.AUTHENTICATION_FAILED.getMessage())
			);
			verify(userQueryRepository).findByLoginId("nonexistent");
		}


		@Test
		@DisplayName("[UserQueryService.authenticate()] 비밀번호 불일치 -> CoreException(AUTHENTICATION_FAILED)")
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
				() -> userQueryService.authenticate("testuser01", "WrongPass1!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_FAILED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.AUTHENTICATION_FAILED.getMessage())
			);
			verify(userQueryRepository).findByLoginId("testuser01");
		}


		@Test
		@DisplayName("[UserQueryService.authenticate()] null loginId -> CoreException(AUTHENTICATION_FAILED). "
			+ "정규화 결과 null이면 DB 조회/인증 없이 즉시 실패")
		void failWhenAuthenticateNullLoginId() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userQueryService.authenticate(null, "Test1234!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_FAILED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.AUTHENTICATION_FAILED.getMessage()),
				() -> verify(userQueryRepository, never()).findByLoginId(any()),
				() -> verify(authenticationManager, never()).authenticate(any(), any())
			);
		}


		@Test
		@DisplayName("[UserQueryService.authenticate()] blank loginId -> CoreException(AUTHENTICATION_FAILED). "
			+ "정규화 결과 null이면 DB 조회/인증 없이 즉시 실패")
		void failWhenAuthenticateBlankLoginId() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userQueryService.authenticate("   ", "Test1234!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_FAILED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.AUTHENTICATION_FAILED.getMessage()),
				() -> verify(userQueryRepository, never()).findByLoginId(any()),
				() -> verify(authenticationManager, never()).authenticate(any(), any())
			);
		}

	}

}
