package com.loopers.user.interfaces.controller;

import com.loopers.user.application.facade.UserCommandFacade;
import com.loopers.user.domain.model.User;
import com.loopers.user.interfaces.controller.request.UserSignUpRequest;
import com.loopers.user.interfaces.controller.response.UserSignUpResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController 테스트")
class UserControllerTest {

	@Mock
	private UserCommandFacade userCommandFacade;

	private UserController userController;

	@BeforeEach
	void setUp() {
		userController = new UserController(userCommandFacade);
	}

	@Test
	@DisplayName("[signUp()] 유효한 요청 -> 201 Created ResponseEntity 반환. 응답 body에 UserSignUpResponse 포함")
	void signUpReturnsCreatedResponse() {
		// Arrange
		UserSignUpRequest request = new UserSignUpRequest(
			"testuser01",
			"Test1234!",
			"홍길동",
			LocalDate.of(1990, 1, 15),
			"test@example.com"
		);

		User savedUser = User.reconstruct(
			1L,
			"testuser01",
			"encodedPassword",
			"홍길동",
			LocalDate.of(1990, 1, 15),
			"test@example.com"
		);

		given(userCommandFacade.signUp(any())).willReturn(savedUser);

		// Act
		ResponseEntity<UserSignUpResponse> response = userController.signUp(request);

		// Assert
		assertAll(
			() -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
			() -> assertThat(response.getBody()).isNotNull(),
			() -> assertThat(response.getBody().id()).isEqualTo(1L),
			() -> assertThat(response.getBody().loginId()).isEqualTo("testuser01"),
			() -> assertThat(response.getBody().name()).isEqualTo("홍길동"),
			() -> assertThat(response.getBody().birthday()).isEqualTo(LocalDate.of(1990, 1, 15)),
			() -> assertThat(response.getBody().email()).isEqualTo("test@example.com")
		);
		verify(userCommandFacade).signUp(any());
	}
}
