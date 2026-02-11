package com.loopers.user.infrastructure.entity;


import com.loopers.user.domain.model.User;
import com.loopers.user.domain.model.vo.LoginId;
import com.loopers.user.domain.model.vo.Password;
import com.loopers.user.infrastructure.mapper.UserEntityMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@DisplayName("UserEntity 테스트")
class UserEntityTest {

	private static final String VALID_LOGIN_ID = "testuser01";
	private static final String VALID_ENCODED_PASSWORD = "encodedPassword";
	private static final String VALID_NAME = "홍길동";
	private static final LocalDate VALID_BIRTHDAY = LocalDate.of(1990, 1, 15);
	private static final String VALID_EMAIL = "test@example.com";
	private final UserEntityMapper userEntityMapper = new UserEntityMapper();


	@Nested
	@DisplayName("도메인 -> 엔티티 변환 테스트")
	class FromDomainTest {

		@Test
		@DisplayName("[UserEntityMapper.toEntity()] User 도메인 -> UserEntity 변환")
		void fromDomain() {
			// Arrange
			User user = User.create(
				LoginId.create(VALID_LOGIN_ID),
				Password.from(VALID_ENCODED_PASSWORD),
				VALID_NAME,
				VALID_BIRTHDAY,
				VALID_EMAIL
			);

			// Act
			UserEntity entity = userEntityMapper.toEntity(user);

			// Assert
			assertAll(
				() -> assertThat(entity).isNotNull(),
				() -> assertThat(entity.getLoginId().getValue()).isEqualTo(VALID_LOGIN_ID),
				() -> assertThat(entity.getPassword().getValue()).isEqualTo(VALID_ENCODED_PASSWORD),
				() -> assertThat(entity.getName()).isEqualTo(VALID_NAME),
				() -> assertThat(entity.getBirthday()).isEqualTo(VALID_BIRTHDAY),
				() -> assertThat(entity.getEmail()).isEqualTo(VALID_EMAIL)
			);
		}

	}


	@Nested
	@DisplayName("엔티티 -> 도메인 변환 테스트")
	class ToDomainTest {

		@Test
		@DisplayName("[UserEntityMapper.toDomain()] UserEntity -> User 도메인 변환")
		void toDomain() {
			// Arrange
			User originalUser = User.create(
				LoginId.create(VALID_LOGIN_ID),
				Password.from(VALID_ENCODED_PASSWORD),
				VALID_NAME,
				VALID_BIRTHDAY,
				VALID_EMAIL
			);
			UserEntity entity = userEntityMapper.toEntity(originalUser);

			// Act
			User reconstructedUser = userEntityMapper.toDomain(entity);

			// Assert
			assertAll(
				() -> assertThat(reconstructedUser).isNotNull(),
				() -> assertThat(reconstructedUser.getLoginId().value()).isEqualTo(VALID_LOGIN_ID),
				() -> assertThat(reconstructedUser.getPassword().value()).isEqualTo(VALID_ENCODED_PASSWORD),
				() -> assertThat(reconstructedUser.getName()).isEqualTo(entity.getName()),
				() -> assertThat(reconstructedUser.getBirthday()).isEqualTo(entity.getBirthday()),
				() -> assertThat(reconstructedUser.getEmail()).isEqualTo(entity.getEmail())
			);
		}

	}

}
