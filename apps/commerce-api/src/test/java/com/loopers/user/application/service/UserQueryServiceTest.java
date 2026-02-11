package com.loopers.user.application.service;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.repository.UserQueryRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("UserQueryService 테스트")
class UserQueryServiceTest {

	@Mock
	private UserQueryRepository userQueryRepository;

	private UserQueryService userQueryService;


	@BeforeEach
	void setUp() {
		userQueryService = new UserQueryService(userQueryRepository);
	}


	private User user(String loginId) {
		return User.reconstruct(
			1L,
			LoginId.create(loginId),
			"encodedPw",
			"홍길동",
			LocalDate.of(1990, 1, 15),
			"test@example.com"
		);
	}


	@Test
	@DisplayName("[UserQueryService.findByLoginId()] 존재하는 loginId -> User 반환")
	void findByLoginIdSuccess() {
		// Arrange
		User user = user("testuser01");
		given(userQueryRepository.findByLoginId("testuser01")).willReturn(Optional.of(user));

		// Act
		User result = userQueryService.findByLoginId("testuser01");

		// Assert
		assertAll(
			() -> assertThat(result.getLoginId().value()).isEqualTo("testuser01"),
			() -> assertThat(result.getName()).isEqualTo("홍길동")
		);
		verify(userQueryRepository).findByLoginId("testuser01");
	}


	@Test
	@DisplayName("[UserQueryService.findByLoginId()] 대문자/공백 loginId -> 정규화된 loginId로 조회")
	void findByLoginIdNormalizesInput() {
		// Arrange
		User user = user("testuser01");
		given(userQueryRepository.findByLoginId("testuser01")).willReturn(Optional.of(user));

		// Act
		User result = userQueryService.findByLoginId("  TESTUSER01  ");

		// Assert
		assertThat(result.getLoginId().value()).isEqualTo("testuser01");
		verify(userQueryRepository).findByLoginId("testuser01");
	}


	@Test
	@DisplayName("[UserQueryService.findByLoginId()] 존재하지 않는 loginId -> USER_NOT_FOUND")
	void findByLoginIdNotFound() {
		// Arrange
		given(userQueryRepository.findByLoginId("nonexistent")).willReturn(Optional.empty());

		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> userQueryService.findByLoginId("nonexistent"));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_NOT_FOUND),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.USER_NOT_FOUND.getMessage())
		);
		verify(userQueryRepository).findByLoginId("nonexistent");
	}


	@Test
	@DisplayName("[UserQueryService.findByLoginId()] null loginId -> USER_NOT_FOUND")
	void findByLoginIdNull() {
		// Arrange
		given(userQueryRepository.findByLoginId(null)).willReturn(Optional.empty());

		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> userQueryService.findByLoginId(null));

		// Assert
		assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_NOT_FOUND);
		verify(userQueryRepository).findByLoginId(null);
	}


	@Nested
	@DisplayName("loginIdDuplicationCheck 테스트")
	class DuplicationTest {

		@Test
		@DisplayName("[UserQueryService.loginIdDuplicationCheck()] 중복 loginId -> USER_ALREADY_EXISTS")
		void loginIdDuplicationCheckTrue() {
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
		@DisplayName("[UserQueryService.loginIdDuplicationCheck()] 미중복 loginId -> 예외 없음")
		void loginIdDuplicationCheckFalse() {
			// Arrange
			given(userQueryRepository.existsByLoginId("nonexistent")).willReturn(false);

			// Act & Assert
			assertDoesNotThrow(() -> userQueryService.loginIdDuplicationCheck("nonexistent"));
			verify(userQueryRepository).existsByLoginId("nonexistent");
		}

	}

}
