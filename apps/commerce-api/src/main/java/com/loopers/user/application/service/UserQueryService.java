package com.loopers.user.application.service;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.repository.UserQueryRepository;
import com.loopers.user.domain.model.vo.LoginId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class UserQueryService {

	// repository
	private final UserQueryRepository userQueryRepository;


	/**
	 * 유저 조회 서비스
	 * 1. 로그인 ID 중복 여부 확인
	 */

	// 1. 로그인 ID 중복 여부 확인
	public void loginIdDuplicationCheck(String rawLoginId) {

		// 로그인 id 정규화
		String normalizedLoginId = LoginId.normalize(rawLoginId);

		// 중복되었다면 예외 발생
		if (userQueryRepository.existsByLoginId(normalizedLoginId)) {
			throw new CoreException(ErrorType.USER_ALREADY_EXISTS);
		}
	}

}
