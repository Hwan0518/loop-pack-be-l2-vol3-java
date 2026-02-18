package com.loopers.user.application.service;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.domain.repository.UserQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
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

}
