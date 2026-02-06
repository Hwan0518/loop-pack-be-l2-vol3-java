package com.loopers.user.application.service;

import com.loopers.user.application.repository.UserQueryRepository;
import com.loopers.user.domain.model.User;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserQueryService {

	private final UserQueryRepository userQueryRepository;

	public UserQueryService(UserQueryRepository userQueryRepository) {
		this.userQueryRepository = userQueryRepository;
	}

	public Optional<User> findByLoginId(String loginId) {
		return userQueryRepository.findByLoginId(loginId);
	}

	public boolean existsByLoginId(String loginId) {
		return userQueryRepository.existsByLoginId(loginId);
	}
}
