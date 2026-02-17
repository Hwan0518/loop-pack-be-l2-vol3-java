package com.loopers.user.infrastructure.mapper;


import com.loopers.user.domain.model.User;
import com.loopers.user.infrastructure.entity.UserEntity;
import com.loopers.user.infrastructure.entity.vo.*;
import org.springframework.stereotype.Component;


@Component
public class UserEntityMapper {

	public UserEntity toEntity(User user) {
		UserEntity entity = UserEntity.of(
			user.getId(),
			UserLoginIdEmbeddable.fromDomain(user.getLoginId()),
			UserPasswordEmbeddable.fromDomain(user.getPassword()),
			UserNameEmbeddable.fromDomain(user.getName()),
			UserBirthdateEmbeddable.fromDomain(user.getBirthDate()),
			UserEmailEmbeddable.fromDomain(user.getEmail())
		);
		if (user.getDeletedAt() != null) {
			entity.delete();
		}
		return entity;
	}


	public User toDomain(UserEntity entity) {
		return User.reconstruct(
			entity.getId(),
			entity.getLoginId().toDomain(),
			entity.getPassword().toDomain(),
			entity.getName().toDomain(),
			entity.getBirthDate().toDomain(),
			entity.getEmail().toDomain(),
			entity.getDeletedAt()
		);
	}

}
