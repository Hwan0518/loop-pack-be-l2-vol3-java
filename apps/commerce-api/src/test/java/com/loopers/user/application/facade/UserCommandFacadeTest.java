package com.loopers.user.application.facade;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.command.UserSignUpCommand;
import com.loopers.user.application.repository.UserQueryRepository;
import com.loopers.user.application.service.UserCommandService;
import com.loopers.user.domain.model.User;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserCommandFacade 테스트")
class UserCommandFacadeTest {

	@Mock
	private UserCommandService userCommandService;

	@Mock
	private UserQueryRepository userQueryRepository;

	private UserCommandFacade userCommandFacade;

	@BeforeEach
	void setUp() {
		userCommandFacade = new UserCommandFacade(userCommandService, userQueryRepository);
	}

	@Nested
	@DisplayName("회원가입 테스트")
	class SignUpTest {

		@Test
		@DisplayName("[UserCommandFacade.signUp()] 유효한 회원가입 정보 -> User 반환. "
			+ "QueryRepository로 중복 체크 후 CommandService로 저장")
		void signUpSuccess() {
			// Arrange
			UserSignUpCommand command = new UserSignUpCommand(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			given(userQueryRepository.existsByLoginId("testuser01")).willReturn(false);
			given(userCommandService.createUser(any(User.class))).willAnswer(invocation -> {
				User user = invocation.getArgument(0);
				return User.reconstruct(
					1L,
					user.getLoginId(),
					user.getPassword().value(),
					user.getName(),
					user.getBirthday(),
					user.getEmail()
				);
			});

			// Act
			User result = userCommandFacade.signUp(command);

			// Assert
			assertAll(
				() -> assertThat(result).isNotNull(),
				() -> assertThat(result.getId()).isEqualTo(1L),
				() -> assertThat(result.getLoginId()).isEqualTo("testuser01"),
				() -> assertThat(result.getName()).isEqualTo("홍길동"),
				() -> assertThat(result.getEmail()).isEqualTo("test@example.com")
			);
			verify(userQueryRepository).existsByLoginId("testuser01");
			verify(userCommandService).createUser(any(User.class));
		}

		@Test
		@DisplayName("[UserCommandFacade.signUp()] 중복된 로그인 ID -> CoreException(ErrorType.USER_ALREADY_EXISTS) 발생. "
			+ "에러 메시지: '이미 가입된 로그인 ID입니다.'")
		void signUpFailWhenLoginIdAlreadyExists() {
			// Arrange
			UserSignUpCommand command = new UserSignUpCommand(
				"existinguser",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			given(userQueryRepository.existsByLoginId("existinguser")).willReturn(true);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandFacade.signUp(command));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_ALREADY_EXISTS),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.USER_ALREADY_EXISTS.getMessage())
			);
			verify(userQueryRepository).existsByLoginId("existinguser");
			verify(userCommandService, never()).createUser(any(User.class));
		}
	}
}
