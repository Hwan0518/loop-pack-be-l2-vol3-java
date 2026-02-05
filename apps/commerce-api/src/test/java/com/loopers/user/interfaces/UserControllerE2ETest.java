package com.loopers.user.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.user.interfaces.controller.request.UserSignUpRequest;
import com.loopers.support.common.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("UserController E2E 테스트")
class UserControllerE2ETest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}

	@Nested
	@DisplayName("POST /api/v1/users - 회원가입")
	class SignUpTest {

		@Test
		@DisplayName("[POST /api/v1/users] 유효한 회원가입 요청 -> 201 Created. "
			+ "응답: id, loginId, name, birthday, email 포함")
		void signUpSuccess() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.loginId").value("testuser01"))
				.andExpect(jsonPath("$.name").value("홍길동"))
				.andExpect(jsonPath("$.email").value("test@example.com"))
				.andExpect(jsonPath("$.birthday").value("1990-01-15"));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 중복 로그인 ID -> 409 Conflict. "
			+ "에러 코드: USER_ALREADY_EXISTS")
		void signUpFailDuplicateLoginId() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			// 첫 번째 회원가입
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated());

			// Act & Assert - 동일 loginId로 재시도
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(ErrorType.USER_ALREADY_EXISTS.getCode()))
				.andExpect(jsonPath("$.message").value(ErrorType.USER_ALREADY_EXISTS.getMessage()));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 비밀번호 형식 오류 (8자 미만) -> 400 Bad Request. "
			+ "에러 코드: INVALID_PASSWORD_FORMAT")
		void signUpFailInvalidPasswordFormat() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"short",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_PASSWORD_FORMAT.getCode()));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 비밀번호에 생년월일 포함 -> 400 Bad Request. "
			+ "에러 코드: PASSWORD_CONTAINS_BIRTHDAY")
		void signUpFailPasswordContainsBirthday() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"Aa19900115!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.PASSWORD_CONTAINS_BIRTHDAY.getCode()));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 로그인 ID 형식 오류 (특수문자 포함) -> 400 Bad Request. "
			+ "에러 코드: INVALID_LOGIN_ID_FORMAT")
		void signUpFailInvalidLoginIdFormat() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"test_user!",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_LOGIN_ID_FORMAT.getCode()));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 이메일 형식 오류 -> 400 Bad Request. "
			+ "에러 코드: INVALID_EMAIL_FORMAT")
		void signUpFailInvalidEmailFormat() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"invalid-email"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_EMAIL_FORMAT.getCode()));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 필수 필드 누락 (birthday, email) -> 400 Bad Request. "
			+ "에러 코드: BAD_REQUEST")
		void signUpFailMissingRequiredFields() throws Exception {
			// Arrange
			String requestJson = """
				{
					"loginId": "testuser01",
					"password": "Test1234!",
					"name": "홍길동"
				}
				""";

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.BAD_REQUEST.getCode()));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 이름 형식 오류 (숫자 포함) -> 400 Bad Request. "
			+ "에러 코드: INVALID_NAME_FORMAT")
		void signUpFailInvalidNameFormat() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"Test1234!",
				"홍길동123",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_NAME_FORMAT.getCode()));
		}

		@Test
		@DisplayName("[POST /api/v1/users] 생년월일 미래 날짜 -> 400 Bad Request. "
			+ "에러 코드: INVALID_BIRTHDAY")
		void signUpFailFutureBirthday() throws Exception {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.now().plusDays(1),
				"test@example.com"
			);

			// Act & Assert
			mockMvc.perform(post("/api/v1/users")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_BIRTHDAY.getCode()));
		}
	}
}
