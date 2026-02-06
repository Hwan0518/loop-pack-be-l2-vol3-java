package com.loopers.user.application.facade;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.out.UserMeOutDto;
import com.loopers.user.application.service.UserQueryService;
import com.loopers.user.domain.model.User;
import com.loopers.user.support.common.HeaderValidator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserQueryFacade {

	private final UserQueryService userQueryService;

	public UserQueryFacade(UserQueryService userQueryService) {
		this.userQueryService = userQueryService;
	}

	@Transactional(readOnly = true)
	public UserMeOutDto getMe(String loginId, String password) {
		HeaderValidator.validate(loginId, password);

		String trimmedLoginId = loginId.trim();
		User user = userQueryService.findByLoginId(trimmedLoginId)
			.orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));

		user.authenticate(password);

		return UserMeOutDto.from(user);
	}
}
