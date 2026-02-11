package com.loopers.user.interfaces.controller;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.out.UserMeOutDto;
import com.loopers.user.application.facade.UserQueryFacade;
import com.loopers.user.interfaces.web.controller.UserQueryController;
import com.loopers.user.interfaces.web.response.UserMeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("UserQueryController 테스트")
class UserQueryControllerTest {

	@Mock
	private UserQueryFacade userQueryFacade;

	private UserQueryController userQueryController;


	@BeforeEach
	void setUp() {
		userQueryController = new UserQueryController(userQueryFacade);
	}


	@Test
	@DisplayName("[UserQueryController.getMe()] 유효한 인증 헤더 -> 200 OK")
	void getMeReturnsOkResponse() {
		// Arrange
		UserMeOutDto outDto = new UserMeOutDto(
			"testuser01",
			"홍길동",
			LocalDate.of(1990, 1, 15),
			"test@example.com"
		);
		given(userQueryFacade.getMe("testuser01", "Test1234!")).willReturn(outDto);

		// Act
		ResponseEntity<UserMeResponse> response = userQueryController.getMe("testuser01", "Test1234!");

		// Assert
		assertAll(
			() -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
			() -> assertThat(response.getBody()).isNotNull(),
			() -> assertThat(response.getBody().loginId()).isEqualTo("testuser01"),
			() -> assertThat(response.getBody().name()).isEqualTo("홍길*")
		);
		verify(userQueryFacade).getMe("testuser01", "Test1234!");
	}


	@Test
	@DisplayName("[UserQueryController.getMe()] 인증 실패 -> CoreException 전파")
	void getMePropagatesException() {
		// Arrange
		given(userQueryFacade.getMe(null, null)).willThrow(new CoreException(ErrorType.UNAUTHORIZED));

		// Act & Assert
		CoreException exception = assertThrows(CoreException.class, () -> userQueryController.getMe(null, null));

		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
		);
	}

}
