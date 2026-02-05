package com.loopers.user.application.service;

import com.loopers.user.application.repository.UserCommandRepository;
import com.loopers.user.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserCommandService {

	private final UserCommandRepository userCommandRepository;

	public UserCommandService(UserCommandRepository userCommandRepository) {
		this.userCommandRepository = userCommandRepository;
	}

	@Transactional
	public User createUser(User user) {
		return userCommandRepository.save(user);
	}
}
