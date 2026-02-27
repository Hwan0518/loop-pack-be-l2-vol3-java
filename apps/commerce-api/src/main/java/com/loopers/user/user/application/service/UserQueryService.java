package com.loopers.user.user.application.service;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.user.application.port.out.util.AuthenticationManager;
import com.loopers.user.user.domain.model.User;
import com.loopers.user.user.domain.repository.UserQueryRepository;
import com.loopers.user.user.domain.model.vo.LoginId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class UserQueryService {

	// repository
	private final UserQueryRepository userQueryRepository;
	// util
	private final AuthenticationManager authenticationManager;


	/**
	 * 유저 조회 서비스
	 * 1. 로그인 ID 중복 여부 확인
	 * 2. 유저 인증
	 */

	// 1. 로그인 ID 중복 여부 확인
	@Transactional(readOnly = true)
	public void loginIdDuplicationCheck(String rawLoginId) {

		// 로그인 id 정규화
		String normalizedLoginId = LoginId.normalize(rawLoginId);

		// 정규화 결과가 null이면 유효하지 않은 loginId
		if (normalizedLoginId == null) {
			throw new CoreException(ErrorType.INVALID_LOGIN_ID_FORMAT);
		}

		// 중복되었다면 예외 발생
		if (userQueryRepository.existsByLoginId(normalizedLoginId)) {
			throw new CoreException(ErrorType.USER_ALREADY_EXISTS);
		}
	}


	// 2. 유저 인증
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
