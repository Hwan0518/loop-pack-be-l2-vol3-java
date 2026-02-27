package com.loopers.user.user.application.service;


import com.loopers.user.user.application.dto.in.UserSignUpInDto;
import com.loopers.user.user.application.port.out.util.PasswordEncoder;
import com.loopers.user.user.domain.repository.UserCommandRepository;
import com.loopers.user.user.domain.model.User;
import com.loopers.user.user.domain.model.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class UserCommandService {

	// repository
	private final UserCommandRepository userCommandRepository;
	// util
	private final PasswordEncoder passwordEncoder;


	/**
	 * 유저 명령 서비스
	 * 1. 유저 생성
	 * 2. 비밀번호 변경
	 */

	// 1. 유저 생성
	@Transactional
	public User createUser(UserSignUpInDto inDto) {

		// VO로 입력값 검증 & 생성: 1,2 순서는 반드시 지켜져야한다
		Birthdate birthdate = Birthdate.create(inDto.birthDate()); // 1

		Password.validatePasswordPolicy(inDto.password(), birthdate.value()); // 2
		String encodedPassword = passwordEncoder.encode(inDto.password());
		Password password = Password.from(encodedPassword);

		LoginId loginId = LoginId.create(inDto.loginId());

		Name name = Name.create(inDto.name());

		Email email = Email.create(inDto.email());

		// 유저 도메인 객체 생성
		User user = User.create(
			loginId,
			password,
			name,
			birthdate,
			email
		);

		// 저장 후 반환
		return userCommandRepository.save(user);
	}


	// 2. 비밀번호 변경
	@Transactional
	public void updatePassword(User user, String newPassword) {

		// 새로운 비밀번호 검증
		Password.validatePasswordPolicy(newPassword, user.getBirthDate().value());
		passwordEncoder.checkPasswordDuplication(user.getPassword().value(), newPassword);

		// 새로운 비밀번호 암호화
		String encodedNewPassword = passwordEncoder.encode(newPassword);

		// 비밀번호 변경 및 저장
		user.changePassword(encodedNewPassword);
		userCommandRepository.save(user);
	}

}
