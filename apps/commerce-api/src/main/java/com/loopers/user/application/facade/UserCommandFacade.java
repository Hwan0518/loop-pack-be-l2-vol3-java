package com.loopers.user.application.facade;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.command.UserChangePasswordCommand;
import com.loopers.user.application.dto.command.UserSignUpCommand;
import com.loopers.user.application.service.UserCommandService;
import com.loopers.user.application.service.UserQueryService;
import com.loopers.user.domain.model.User;
import com.loopers.user.support.common.HeaderValidator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserCommandFacade {

	private final UserCommandService userCommandService;
	private final UserQueryService userQueryService;

	public UserCommandFacade(UserCommandService userCommandService, UserQueryService userQueryService) {
		this.userCommandService = userCommandService;
		this.userQueryService = userQueryService;
	}

	@Transactional
	public User signUp(UserSignUpCommand command) {
		String normalizedLoginId = command.loginId() != null
			? command.loginId().trim().toLowerCase()
			: command.loginId();

		if (userQueryService.existsByLoginId(normalizedLoginId)) {
			throw new CoreException(ErrorType.USER_ALREADY_EXISTS);
		}

		User user = User.create(
			command.loginId(),
			command.password(),
			command.name(),
			command.birthday(),
			command.email()
		);

		return userCommandService.createUser(user);
	}

	@Transactional
	public void changePassword(String loginId, String headerPassword, UserChangePasswordCommand command) {
		HeaderValidator.validate(loginId, headerPassword);

		User user = userQueryService.findByLoginId(loginId.trim())
			.orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));

		user.authenticate(headerPassword);
		user.changePassword(command.currentPassword(), command.newPassword());

		userCommandService.updateUser(user);
	}
}
