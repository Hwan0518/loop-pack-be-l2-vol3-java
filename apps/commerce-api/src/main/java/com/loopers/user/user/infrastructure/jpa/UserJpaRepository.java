package com.loopers.user.user.infrastructure.jpa;

import com.loopers.user.user.infrastructure.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

	/**
	 * 유저 JPA 리포지토리
	 * 1. 로그인 ID로 활성 유저 엔티티 조회
	 * 2. 활성 유저 로그인 ID 중복 여부 확인
	 */

	// 1. 로그인 ID로 활성 유저 엔티티 조회
	Optional<UserEntity> findByLoginIdValueAndDeletedAtIsNull(String loginId);

	// 2. 활성 유저 로그인 ID 중복 여부 확인
	boolean existsByLoginIdValueAndDeletedAtIsNull(String loginId);
}
