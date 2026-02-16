package com.loopers.user.infrastructure.entity;


import com.loopers.user.domain.model.User;
import com.loopers.user.domain.model.vo.*;
import com.loopers.user.infrastructure.mapper.UserEntityMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@DisplayName("UserEntity 테스트")
class UserEntityTest {

	private static final LoginId VALID_LOGIN_ID = LoginId.create("testuser01");
	private static final Password VALID_PASSWORD = Password.from("encodedPassword");
	private static final Name VALID_NAME = Name.create("홍길동");
	private static final Birthdate VALID_BIRTH_DATE = Birthdate.create(LocalDate.of(1990, 1, 15));
	private static final Email VALID_EMAIL = Email.create("test@example.com");
	private final UserEntityMapper userEntityMapper = new UserEntityMapper();

	@Nested
	@DisplayName("도메인 -> 엔티티 변환 테스트")
	class FromDomainTest {

		@Test
		@DisplayName("[from()] User 도메인 -> UserEntity 변환. "
			+ "loginId, password, name, birthDate, email이 정확히 매핑됨")
		void fromDomain() {
			// Arrange
			User user = User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

			// Act
			UserEntity entity = userEntityMapper.toEntity(user);

			// Assert
			assertAll(
				() -> assertThat(entity).isNotNull(),
				() -> assertThat(entity.getLoginId().toDomain().value()).isEqualTo("testuser01"),
				() -> assertThat(entity.getPassword().toDomain().value()).isEqualTo(user.getPassword().value()),
				() -> assertThat(entity.getName().toDomain().value()).isEqualTo("홍길동"),
				() -> assertThat(entity.getBirthDate().toDomain().value()).isEqualTo(LocalDate.of(1990, 1, 15)),
				() -> assertThat(entity.getEmail().toDomain().value()).isEqualTo("test@example.com")
			);
		}

	}

	@Nested
	@DisplayName("엔티티 -> 도메인 변환 테스트")
	class ToDomainTest {

		@Test
		@DisplayName("[toDomain()] UserEntity -> User 도메인 변환. "
			+ "User.reconstruct()를 통해 id, loginId, password, name, birthDate, email이 정확히 복원됨")
		void toDomain() {
			// Arrange
			User originalUser = User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
			UserEntity entity = userEntityMapper.toEntity(originalUser);

			// Act
			User reconstructedUser = userEntityMapper.toDomain(entity);

			// Assert
			assertAll(
				() -> assertThat(reconstructedUser).isNotNull(),
				() -> assertThat(reconstructedUser.getId()).isEqualTo(entity.getId()),
				() -> assertThat(reconstructedUser.getLoginId().value()).isEqualTo(entity.getLoginId().toDomain().value()),
				() -> assertThat(reconstructedUser.getPassword().value()).isEqualTo(entity.getPassword().toDomain().value()),
				() -> assertThat(reconstructedUser.getName().value()).isEqualTo(entity.getName().toDomain().value()),
				() -> assertThat(reconstructedUser.getBirthDate().value()).isEqualTo(entity.getBirthDate().toDomain().value()),
				() -> assertThat(reconstructedUser.getEmail().value()).isEqualTo(entity.getEmail().toDomain().value()),
			() -> assertThat(reconstructedUser.getDeletedAt()).isEqualTo(entity.getDeletedAt())
			);
		}

	}

}
