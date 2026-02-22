package com.loopers.user.infrastructure.repository;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.user.domain.repository.UserCommandRepository;
import com.loopers.user.domain.model.User;
import com.loopers.user.domain.model.vo.*;
import com.loopers.user.infrastructure.jpa.UserJpaRepository;
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
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("UserCommandRepository 테스트")
class UserCommandRepositoryTest {

	@Autowired
	private UserCommandRepository userCommandRepository;

	@Autowired
	private UserJpaRepository userJpaRepository;

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
	@DisplayName("[UserCommandRepository.save()] active 유저와 동일 loginId 저장 -> CoreException(USER_ALREADY_EXISTS). DB 유니크 제약이 동시성 문제 방지")
	void saveActiveDuplicateLoginIdThrows() {
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
		userCommandRepository.save(firstUser);

		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> userCommandRepository.save(duplicateLoginIdUser));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_ALREADY_EXISTS),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.USER_ALREADY_EXISTS.getMessage())
		);
	}

	@Test
	@DisplayName("[UserCommandRepository.save()] soft delete된 유저의 loginId로 새 유저 저장 -> 정상 저장. soft delete 정책에 따라 loginId 재사용 허용")
	void saveSoftDeletedLoginIdReuse() {
		// Arrange
		User firstUser = User.create(
			LoginId.create("testuser01"),
			Password.from("encodedPassword1"),
			Name.create("홍길동"),
			Birthdate.create(LocalDate.of(1990, 1, 15)),
			Email.create("test@example.com")
		);
		User savedFirstUser = userCommandRepository.save(firstUser);

		// soft delete
		userJpaRepository.findById(savedFirstUser.getId()).ifPresent(entity -> {
			entity.delete();
			userJpaRepository.save(entity);
		});

		User newUser = User.create(
			LoginId.create("testuser01"),
			Password.from("encodedPassword2"),
			Name.create("김철수"),
			Birthdate.create(LocalDate.of(1991, 2, 2)),
			Email.create("other@example.com")
		);

		// Act
		User savedNewUser = assertDoesNotThrow(() -> userCommandRepository.save(newUser));

		// Assert
		assertAll(
			() -> assertThat(savedNewUser.getId()).isNotNull(),
			() -> assertThat(savedNewUser.getId()).isNotEqualTo(savedFirstUser.getId()),
			() -> assertThat(savedNewUser.getLoginId().value()).isEqualTo("testuser01")
		);
	}
}
