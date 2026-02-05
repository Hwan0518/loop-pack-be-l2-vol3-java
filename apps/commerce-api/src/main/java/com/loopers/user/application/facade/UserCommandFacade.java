package com.loopers.user.application.facade;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.command.UserSignUpCommand;
import com.loopers.user.application.repository.UserQueryRepository;
import com.loopers.user.application.service.UserCommandService;
import com.loopers.user.domain.model.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserCommandFacade {

	private final UserCommandService userCommandService;
	private final UserQueryRepository userQueryRepository;

	public UserCommandFacade(UserCommandService userCommandService, UserQueryRepository userQueryRepository) {
		this.userCommandService = userCommandService;
		this.userQueryRepository = userQueryRepository;
	}

	@Transactional
	public User signUp(UserSignUpCommand command) {
		if (userQueryRepository.existsByLoginId(command.loginId())) {
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
}
