package com.loopers.user.user.interfaces.web.controller;


import com.loopers.user.user.application.dto.out.UserMeOutDto;
import com.loopers.user.user.application.facade.UserQueryFacade;
import com.loopers.user.user.interfaces.web.response.UserMeResponse;
import com.loopers.support.common.auth.HeaderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserQueryController {

	// service
	private final UserQueryFacade userQueryFacade;


	/**
	 * 유저 컨트롤러
	 * 1. 내 정보 조회
	 */

	// 1. 내 정보 조회
	@GetMapping("/me")
	public ResponseEntity<UserMeResponse> getMe(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password
	) {

		// 인증 헤더 필수값 검증
		HeaderValidator.validate(loginId, password);

		// 내 정보 조회
		UserMeOutDto outDto = userQueryFacade.getMe(loginId, password);

		// 조회 결과 DTO를 응답 객체로 변환
		UserMeResponse response = UserMeResponse.from(outDto);

		// 200 OK와 내 정보 응답 반환
		return ResponseEntity.ok(response);
	}

}
