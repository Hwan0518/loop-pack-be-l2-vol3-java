package com.loopers.user.interfaces.controller;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.in.UserChangePasswordInDto;
import com.loopers.user.application.dto.out.UserSignUpOutDto;
import com.loopers.user.application.facade.UserCommandFacade;
import com.loopers.user.interfaces.web.controller.UserCommandController;
import com.loopers.user.interfaces.web.request.UserChangePasswordRequest;
import com.loopers.user.interfaces.web.request.UserSignUpRequest;
import com.loopers.user.interfaces.web.response.UserSignUpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.willThrow;


@ExtendWith(MockitoExtension.class)
@DisplayName("UserCommandController 테스트")
class UserCommandControllerTest {

	@Mock
	private UserCommandFacade userCommandFacade;

	private UserCommandController userCommandController;


	@BeforeEach
	void setUp() {
		userCommandController = new UserCommandController(userCommandFacade);
	}


	@Test
	@DisplayName("[UserCommandController.signUp()] 유효한 요청 -> 201 Created")
	void signUpReturnsCreatedResponse() {
		// Arrange
		UserSignUpRequest request = new UserSignUpRequest(
			"testuser01",
			"Test1234!",
			"홍길동",
			LocalDate.of(1990, 1, 15),
			"test@example.com"
		);
		UserSignUpOutDto outDto = new UserSignUpOutDto(
			1L,
			"testuser01",
			"홍길동",
			LocalDate.of(1990, 1, 15),
			"test@example.com"
		);
		given(userCommandFacade.signUp(any())).willReturn(outDto);

		// Act
		ResponseEntity<UserSignUpResponse> response = userCommandController.signUp(request);

		// Assert
		assertAll(
			() -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
			() -> assertThat(response.getBody()).isNotNull(),
			() -> assertThat(response.getBody().id()).isEqualTo(1L),
			() -> assertThat(response.getBody().loginId()).isEqualTo("testuser01")
		);
		verify(userCommandFacade).signUp(any());
	}


	@Test
	@DisplayName("[UserCommandController.changePassword()] 헤더+요청 매핑 -> facade.changePassword() 위임")
	void changePasswordMapsAndDelegates() {
		// Arrange
		UserChangePasswordRequest request = new UserChangePasswordRequest("NewPass1234!");

		// Act
		ResponseEntity<Void> response = userCommandController.changePassword("testuser01", "Test1234!", request);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		ArgumentCaptor<UserChangePasswordInDto> captor = ArgumentCaptor.forClass(UserChangePasswordInDto.class);
		verify(userCommandFacade).changePassword(captor.capture());
		assertAll(
			() -> assertThat(captor.getValue().loginId()).isEqualTo("testuser01"),
			() -> assertThat(captor.getValue().currentPassword()).isEqualTo("Test1234!"),
			() -> assertThat(captor.getValue().newPassword()).isEqualTo("NewPass1234!")
		);
	}


	@Test
	@DisplayName("[UserCommandController.changePassword()] Facade 예외 전파")
	void changePasswordPropagatesException() {
		// Arrange
		UserChangePasswordRequest request = new UserChangePasswordRequest("NewPass1234!");
		willThrow(new CoreException(ErrorType.UNAUTHORIZED))
			.given(userCommandFacade).changePassword(any(UserChangePasswordInDto.class));

		// Act & Assert
		CoreException exception = assertThrows(CoreException.class,
			() -> userCommandController.changePassword("testuser01", "WrongPass1!", request));

		assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
	}

}
