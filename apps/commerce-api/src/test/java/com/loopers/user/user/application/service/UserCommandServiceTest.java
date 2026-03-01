package com.loopers.user.user.application.service;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.user.application.dto.in.UserSignUpInDto;
import com.loopers.user.user.application.port.out.util.PasswordEncoder;
import com.loopers.user.user.domain.repository.UserCommandRepository;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("UserCommandService 테스트")
class UserCommandServiceTest {

	@Mock
	private UserCommandRepository userCommandRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	private UserCommandService userCommandService;


	@BeforeEach
	void setUp() {
		userCommandService = new UserCommandService(userCommandRepository, passwordEncoder);
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
	@DisplayName("비밀번호 변경 테스트")
	class UpdatePasswordTest {

		@Test
		@DisplayName("[UserCommandService.updatePassword()] 유효한 User + 새 비밀번호 -> 비밀번호 검증/암호화/변경/저장")
		void updatePasswordSuccess() {
			// Arrange
			User user = User.reconstruct(1L, LoginId.from("testuser01"), Password.from("encodedPw"),
				Name.from("홍길동"), Birthdate.from(LocalDate.of(1990, 1, 15)),
				Email.from("test@example.com"), null);
			willDoNothing().given(passwordEncoder).checkPasswordDuplication("encodedPw", "NewPass1234!");
			given(passwordEncoder.encode("NewPass1234!")).willReturn("newEncodedPw");
			given(userCommandRepository.save(any(User.class))).willReturn(user);

			// Act & Assert
			assertDoesNotThrow(() -> userCommandService.updatePassword(user, "NewPass1234!"));

			verify(passwordEncoder).checkPasswordDuplication("encodedPw", "NewPass1234!");
			verify(passwordEncoder).encode("NewPass1234!");
			verify(userCommandRepository).save(user);
		}


		@Test
		@DisplayName("[UserCommandService.updatePassword()] 새 비밀번호가 현재 비밀번호와 동일 -> CoreException(PASSWORD_SAME_AS_CURRENT)")
		void failWhenNewPasswordSameAsCurrent() {
			// Arrange
			User user = User.reconstruct(1L, LoginId.from("testuser01"), Password.from("encodedPw"),
				Name.from("홍길동"), Birthdate.from(LocalDate.of(1990, 1, 15)),
				Email.from("test@example.com"), null);
			willThrow(new CoreException(ErrorType.PASSWORD_SAME_AS_CURRENT))
				.given(passwordEncoder).checkPasswordDuplication("encodedPw", "Test1234!");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandService.updatePassword(user, "Test1234!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT.getMessage())
			);
			verify(userCommandRepository, never()).save(any(User.class));
		}

	}

}
