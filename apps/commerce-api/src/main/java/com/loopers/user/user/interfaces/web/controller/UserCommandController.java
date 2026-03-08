package com.loopers.user.user.interfaces.web.controller;


import com.loopers.user.user.application.dto.in.UserChangePasswordInDto;
import com.loopers.user.user.application.dto.in.UserSignUpInDto;
import com.loopers.user.user.application.dto.out.UserSignUpOutDto;
import com.loopers.user.user.application.facade.UserCommandFacade;
import com.loopers.user.user.interfaces.web.request.UserChangePasswordRequest;
import com.loopers.user.user.interfaces.web.request.UserSignUpRequest;
import com.loopers.user.user.interfaces.web.response.UserSignUpResponse;
import com.loopers.support.common.auth.HeaderValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserCommandController {

	// service
	private final UserCommandFacade userCommandFacade;


	/**
	 * 유저 컨트롤러
	 * 1. 회원가입
	 * 2. 내 비밀번호 변경
	 */

	// 1. 회원가입
	@PostMapping
	public ResponseEntity<UserSignUpResponse> signUp(@Valid @RequestBody UserSignUpRequest request) {

		// mapping
		UserSignUpInDto inDto = new UserSignUpInDto(
			request.loginId(),
			request.password(),
			request.name(),
			request.birthDate(),
			request.email()
		);

		// 회원가입 요청 DTO 변환 후 가입 처리
		UserSignUpOutDto outDto = userCommandFacade.signUp(inDto);

		// 회원가입 결과 DTO를 응답 객체로 변환
		UserSignUpResponse response = UserSignUpResponse.from(outDto);

		// 201 Created와 회원 정보 응답 반환
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}


	// 2. 내 비밀번호 변경
	@PatchMapping("/me/password")
	public ResponseEntity<Void> changePassword(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@Valid @RequestBody UserChangePasswordRequest request
	) {

		// 인증 헤더 필수값 검증
		HeaderValidator.validate(loginId, password);

		// mapping
		UserChangePasswordInDto inDto = new UserChangePasswordInDto(
			loginId,
			password,
			request.newPassword()
		);

		// 비밀번호 변경 요청 DTO 변환 후 변경 처리
		userCommandFacade.changePassword(inDto);

		// 200 OK 반환
		return ResponseEntity.ok().build();
	}

}
