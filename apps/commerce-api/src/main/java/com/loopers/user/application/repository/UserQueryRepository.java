package com.loopers.user.application.repository;

import com.loopers.user.domain.model.User;

import java.util.Optional;

public interface UserQueryRepository {

	Optional<User> findByLoginId(String loginId);

	boolean existsByLoginId(String loginId);
}
