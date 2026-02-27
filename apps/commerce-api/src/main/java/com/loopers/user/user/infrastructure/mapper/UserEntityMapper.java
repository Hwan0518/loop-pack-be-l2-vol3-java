package com.loopers.user.user.infrastructure.mapper;


import com.loopers.user.user.domain.model.User;
import com.loopers.user.user.domain.model.vo.*;
import com.loopers.user.user.infrastructure.entity.UserEntity;
import com.loopers.user.user.infrastructure.entity.vo.*;
import org.springframework.stereotype.Component;


/**
 * 유저 엔티티 매퍼
 * 1. Domain -> Entity 변환
 * 2. Entity -> Domain 변환
 */
@Component
public class UserEntityMapper {

	// 1. Domain -> Entity 변환
	public UserEntity toEntity(User user) {
		UserEntity entity = UserEntity.of(
			user.getId(),
			UserLoginIdEmbeddable.of(user.getLoginId().value()),
			UserPasswordEmbeddable.of(user.getPassword().value()),
			UserNameEmbeddable.of(user.getName().value()),
			UserBirthdateEmbeddable.of(user.getBirthDate().value()),
			UserEmailEmbeddable.of(user.getEmail().value())
		);
		if (user.getDeletedAt() != null) {
			entity.delete();
		}
		return entity;
	}


	// 2. Entity -> Domain 변환
	public User toDomain(UserEntity entity) {
		return User.reconstruct(
			entity.getId(),
			LoginId.from(entity.getLoginId().getValue()),
			Password.from(entity.getPassword().getValue()),
			Name.from(entity.getName().getValue()),
			Birthdate.from(entity.getBirthDate().getValue()),
			Email.from(entity.getEmail().getValue()),
			entity.getDeletedAt()
		);
	}

}
