package com.loopers.user.domain.repository;


import com.loopers.user.domain.model.User;


public interface UserCommandRepository {

	/**
	 * 유저 명령 리포지토리
	 * 1. 유저 저장
	 * 2. 유저 수정
	 */

	// 1. 유저 저장
	User save(User user);

}
