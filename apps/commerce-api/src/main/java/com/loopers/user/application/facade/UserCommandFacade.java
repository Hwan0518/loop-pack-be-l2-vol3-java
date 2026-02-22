package com.loopers.user.application.facade;


import com.loopers.user.application.dto.in.UserChangePasswordInDto;
import com.loopers.user.application.dto.in.UserSignUpInDto;
import com.loopers.user.application.dto.out.UserSignUpOutDto;
import com.loopers.user.application.service.UserCommandService;
import com.loopers.user.application.service.UserQueryService;
import com.loopers.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class UserCommandFacade {

	// service
	private final UserCommandService userCommandService;
	private final UserQueryService userQueryService;


	/**
	 * 유저 명령 파사드
	 * 1. 회원가입
	 * 2. 내 비밀번호 변경
	 */

	// 1. 회원가입
	@Transactional
	public UserSignUpOutDto signUp(UserSignUpInDto inDto) {

		// 로그인 ID 중복 검증
		userQueryService.loginIdDuplicationCheck(inDto.loginId());

		// 유저 생성
		User savedUser = userCommandService.createUser(inDto);

		// 결과 응답 DTO로 변환
		return UserSignUpOutDto.from(savedUser);
	}


	// 2. 내 비밀번호 변경
	@Transactional
	public void changePassword(UserChangePasswordInDto inDto) {

		// 비밀번호 변경
		userCommandService.updatePassword(inDto);
	}

}
