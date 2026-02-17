package com.loopers.user.application.service;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.in.UserChangePasswordInDto;
import com.loopers.user.application.dto.in.UserSignUpInDto;
import com.loopers.user.application.port.AuthenticationManager;
import com.loopers.user.application.port.PasswordEncoder;
import com.loopers.user.application.repository.UserCommandRepository;
import com.loopers.user.application.repository.UserQueryRepository;
import com.loopers.user.domain.model.User;
import com.loopers.user.domain.model.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class UserCommandService {

	// repository
	private final UserQueryRepository userQueryRepository;
	private final UserCommandRepository userCommandRepository;
	// util
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;


	/**
	 * 유저 명령 서비스
	 * 1. 유저 생성
	 * 2. 비밀번호 변경
	 * 3. 유저 인증
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
	public void updatePassword(UserChangePasswordInDto inDto) {

		// 유저 인증
		User user = this.authenticate(inDto.loginId(), inDto.currentPassword());

		// 새로운 비밀번호 검증
		Password.validatePasswordPolicy(inDto.newPassword(), user.getBirthDate().value());
		passwordEncoder.checkPasswordDuplication(user.getPassword().value(), inDto.newPassword());

		// 새로운 비밀번호 암호화
		String encodedNewPassword = passwordEncoder.encode(inDto.newPassword());

		// 비밀번호 변경 및 저장
		user.changePassword(encodedNewPassword);
		userCommandRepository.save(user);
	}


	// 3. 유저 인증
	@Transactional(readOnly = true)
	public User authenticate(String rawLoginId, String inputPassword) {

		// 로그인 ID로 유저 조회
		String normalizedLoginId = LoginId.normalize(rawLoginId);
		User user = userQueryRepository.findByLoginId(normalizedLoginId)
			.orElseThrow(() -> new CoreException(ErrorType.AUTHENTICATION_FAILED));

		// 비밀번호 인증
		authenticationManager.authenticate(user, inputPassword);

		// 유저 반환
		return user;
	}

}
