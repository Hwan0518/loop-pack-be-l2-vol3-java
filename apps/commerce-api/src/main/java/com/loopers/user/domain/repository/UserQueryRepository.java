package com.loopers.user.domain.repository;

import com.loopers.user.domain.model.User;

import java.util.Optional;

public interface UserQueryRepository {

	/**
	 * 유저 조회 리포지토리
	 * 1. 로그인 ID로 유저 조회
	 * 2. 로그인 ID 중복 여부 확인
	 */

	// 1. 로그인 ID로 유저 조회
	Optional<User> findByLoginId(String loginId);

	// 2. 로그인 ID 중복 여부 확인
	boolean existsByLoginId(String loginId);
}
