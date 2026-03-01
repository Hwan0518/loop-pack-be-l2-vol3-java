package com.loopers.user.user.infrastructure.repository;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.user.domain.repository.UserCommandRepository;
import com.loopers.user.user.domain.model.User;
import com.loopers.user.user.infrastructure.entity.UserEntity;
import com.loopers.user.user.infrastructure.jpa.UserJpaRepository;
import com.loopers.user.user.infrastructure.mapper.UserEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;


@Repository
@RequiredArgsConstructor
public class UserCommandRepositoryImpl implements UserCommandRepository {

	// jpa
	private final UserJpaRepository userJpaRepository;
	// mapper
	private final UserEntityMapper userMapper;


	/**
	 * 유저 명령 리포지토리 구현체
	 * 1. 유저 저장
	 * 2. 유저 수정
	 */

	// 1. 유저 저장
	@Override
	public User save(User user) {

		// mapping
		UserEntity entity = userMapper.toEntity(user);

		// 엔티티 저장 후 도메인 객체로 변환
		try {
			return userMapper.toDomain(userJpaRepository.save(entity));
		} catch (DataIntegrityViolationException e) {
			if (isDuplicateLoginIdError(e)) {
				throw new CoreException(ErrorType.USER_ALREADY_EXISTS);
			}
			throw e;
		}
	}


	private boolean isDuplicateLoginIdError(DataIntegrityViolationException e) {
		return e.getMessage() != null && e.getMessage().contains("uk_active_login_id");
	}

}
