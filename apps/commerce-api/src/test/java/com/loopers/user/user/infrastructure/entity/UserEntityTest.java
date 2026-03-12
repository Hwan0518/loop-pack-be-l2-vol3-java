package com.loopers.user.user.infrastructure.entity;


import com.loopers.user.user.domain.model.User;
import com.loopers.user.user.domain.model.vo.*;
import com.loopers.user.user.infrastructure.mapper.UserEntityMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZonedDateTime;

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
		@DisplayName("[toEntity()] 활성 User -> UserEntity 변환. "
			+ "loginId, password, name, birthDate, email이 정확히 매핑되며, deletedAt은 null")
		void fromActiveDomain() {
			// Arrange
			User user = User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

			// Act
			UserEntity entity = userEntityMapper.toEntity(user);

			// Assert
			assertAll(
				() -> assertThat(entity.getLoginId().getValue()).isEqualTo("testuser01"),
				() -> assertThat(entity.getPassword().getValue()).isEqualTo(user.getPassword().value()),
				() -> assertThat(entity.getName().getValue()).isEqualTo("홍길동"),
				() -> assertThat(entity.getBirthDate().getValue()).isEqualTo(LocalDate.of(1990, 1, 15)),
				() -> assertThat(entity.getEmail().getValue()).isEqualTo("test@example.com"),
				() -> assertThat(entity.getDeletedAt()).isNull()
			);
		}

		@Test
		@DisplayName("[toEntity()] 삭제된 User -> UserEntity 변환. deletedAt이 설정됨")
		void fromDeletedDomain() {
			// Arrange
			ZonedDateTime deletedAt = ZonedDateTime.now();
			User deletedUser = User.reconstruct(1L, LoginId.from("testuser01"),
				Password.from("encodedPassword"), Name.from("홍길동"),
				Birthdate.from(LocalDate.of(1990, 1, 15)), Email.from("test@example.com"),
				deletedAt);

			// Act
			UserEntity entity = userEntityMapper.toEntity(deletedUser);

			// Assert — Soft Delete 조건 분기 검증
			assertThat(entity.getDeletedAt()).isNotNull();
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
				() -> assertThat(reconstructedUser.getId()).isEqualTo(entity.getId()),
				() -> assertThat(reconstructedUser.getLoginId().value()).isEqualTo(entity.getLoginId().getValue()),
				() -> assertThat(reconstructedUser.getPassword().value()).isEqualTo(entity.getPassword().getValue()),
				() -> assertThat(reconstructedUser.getName().value()).isEqualTo(entity.getName().getValue()),
				() -> assertThat(reconstructedUser.getBirthDate().value()).isEqualTo(entity.getBirthDate().getValue()),
				() -> assertThat(reconstructedUser.getEmail().value()).isEqualTo(entity.getEmail().getValue()),
				() -> assertThat(reconstructedUser.getDeletedAt()).isEqualTo(entity.getDeletedAt())
			);
		}

	}

	@Nested
	@DisplayName("양방향 변환 일관성 테스트")
	class RoundTripTest {

		@Test
		@DisplayName("[toEntity() → toDomain()] 도메인 → 엔티티 → 도메인 변환 시 비즈니스 필드 보존. "
			+ "loginId, password, name, birthDate, email 값이 원본과 동일")
		void roundTripPreservesFields() {
			// Arrange
			User original = User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

			// Act
			UserEntity entity = userEntityMapper.toEntity(original);
			User reconstructed = userEntityMapper.toDomain(entity);

			// Assert — 원본 도메인과 복원된 도메인의 비즈니스 필드 일치 검증
			assertAll(
				() -> assertThat(reconstructed.getLoginId().value()).isEqualTo(original.getLoginId().value()),
				() -> assertThat(reconstructed.getPassword().value()).isEqualTo(original.getPassword().value()),
				() -> assertThat(reconstructed.getName().value()).isEqualTo(original.getName().value()),
				() -> assertThat(reconstructed.getBirthDate().value()).isEqualTo(original.getBirthDate().value()),
				() -> assertThat(reconstructed.getEmail().value()).isEqualTo(original.getEmail().value()),
				() -> assertThat(reconstructed.getDeletedAt()).isNull()
			);
		}

	}

}
