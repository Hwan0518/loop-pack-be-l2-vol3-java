package com.loopers.user.infrastructure.repository;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.user.application.repository.UserCommandRepository;
import com.loopers.user.domain.model.User;
import com.loopers.user.domain.model.vo.*;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("UserCommandRepository 테스트")
class UserCommandRepositoryTest {

	@Autowired
	private UserCommandRepository userCommandRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}

	@Test
	@DisplayName("[UserCommandRepository.save()] 유효한 User 저장 -> ID가 할당된 User 반환")
	void saveUser() {
		// Arrange
		User user = User.create(
			LoginId.create("testuser01"),
			Password.from("encodedPassword"),
			Name.create("홍길동"),
			Birthdate.create(LocalDate.of(1990, 1, 15)),
			Email.create("test@example.com")
		);

		// Act
		User savedUser = userCommandRepository.save(user);

		// Assert
		assertAll(
			() -> assertThat(savedUser.getId()).isNotNull(),
			() -> assertThat(savedUser.getLoginId().value()).isEqualTo("testuser01"),
			() -> assertThat(savedUser.getName().value()).isEqualTo("홍길동")
		);
	}

	@Test
	@DisplayName("[UserCommandRepository.save()] 중복 loginId 저장 시도 -> 정상 저장")
	void saveDuplicateLoginId() {
		// Arrange
		User firstUser = User.create(
			LoginId.create("testuser01"),
			Password.from("encodedPassword1"),
			Name.create("홍길동"),
			Birthdate.create(LocalDate.of(1990, 1, 15)),
			Email.create("test@example.com")
		);
		User duplicateLoginIdUser = User.create(
			LoginId.create("testuser01"),
			Password.from("encodedPassword2"),
			Name.create("김철수"),
			Birthdate.create(LocalDate.of(1991, 2, 2)),
			Email.create("other@example.com")
		);
		User savedFirstUser = userCommandRepository.save(firstUser);

		// Act
		User savedDuplicateLoginIdUser = assertDoesNotThrow(
			() -> userCommandRepository.save(duplicateLoginIdUser)
		);

		// Assert
		assertAll(
			() -> assertThat(savedFirstUser.getId()).isNotNull(),
			() -> assertThat(savedDuplicateLoginIdUser.getId()).isNotNull(),
			() -> assertThat(savedDuplicateLoginIdUser.getId()).isNotEqualTo(savedFirstUser.getId()),
			() -> assertThat(savedDuplicateLoginIdUser.getLoginId().value()).isEqualTo("testuser01")
		);
	}
}
