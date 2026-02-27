package com.loopers.user.user.domain.repository;


import com.loopers.user.user.domain.model.User;


public interface UserCommandRepository {

	/**
	 * 유저 명령 리포지토리
	 * 1. 유저 저장
	 */

	// 1. 유저 저장
	User save(User user);

}
