package com.loopers.user.user.application.facade;


import com.loopers.user.user.application.dto.out.UserMeOutDto;
import com.loopers.user.user.application.service.UserQueryService;
import com.loopers.user.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class UserQueryFacade {

	// service
	private final UserQueryService userQueryService;


	/**
	 * 유저 조회 파사드
	 * 1. 내 정보 조회
	 * 2. 사용자 인증 후 사용자 ID 반환 (Cross-BC 전용 — ACL에서 호출)
	 */

	// 1. 내 정보 조회
	@Transactional(readOnly = true)
	public UserMeOutDto getMe(String rawLoginId, String password) {

		// 유저 인증
		User user = userQueryService.authenticate(rawLoginId, password);

		// 조회 결과를 응답 DTO로 변환
		return UserMeOutDto.from(user);
	}


	// 2. 사용자 인증 후 사용자 ID 반환 (Cross-BC 전용 — ACL에서 호출)
	@Transactional(readOnly = true)
	public Long authenticateAndGetUserId(String rawLoginId, String password) {

		// 유저 인증
		User user = userQueryService.authenticate(rawLoginId, password);

		// 인증된 사용자의 ID 반환
		return user.getId();
	}

}
