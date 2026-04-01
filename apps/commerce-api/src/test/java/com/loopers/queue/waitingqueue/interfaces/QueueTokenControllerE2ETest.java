package com.loopers.queue.waitingqueue.interfaces;


import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.support.common.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.user.user.interfaces.web.request.UserSignUpRequest;
import com.loopers.utils.DatabaseCleanUp;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("QueueTokenController E2E 테스트")
class QueueTokenControllerE2ETest {

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


	// helper — 회원가입
	private void signUpUser(String loginId, String password) throws Exception {
		UserSignUpRequest request = new UserSignUpRequest(
			loginId, password, "테스트유저", LocalDate.of(1990, 1, 1), "test@test.com"
		);
		mockMvc.perform(post("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}


	@Nested
	@DisplayName("POST /auth/queue-token - 대기열 토큰 발급")
	class IssueQueueTokenTest {

		@Test
		@DisplayName("[POST /auth/queue-token] 유효한 인증 헤더 -> 200 OK. 응답: queueToken 포함")
		void issueQueueToken_validCredentials_returnsToken() throws Exception {
			// Arrange
			signUpUser("testuser01", "Test1234!");

			// Act & Assert
			mockMvc.perform(post("/auth/queue-token")
					.header("X-Loopers-LoginId", "testuser01")
					.header("X-Loopers-LoginPw", "Test1234!"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.queueToken").value(notNullValue()));
		}


		@Test
		@DisplayName("[POST /auth/queue-token] 잘못된 비밀번호 -> 401 Unauthorized. AUTHENTICATION_FAILED")
		void issueQueueToken_wrongPassword_returns401() throws Exception {
			// Arrange
			signUpUser("testuser01", "Test1234!");

			// Act & Assert
			mockMvc.perform(post("/auth/queue-token")
					.header("X-Loopers-LoginId", "testuser01")
					.header("X-Loopers-LoginPw", "WrongPass1!"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}


		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {"  ", "\t"})
		@DisplayName("[POST /auth/queue-token] LoginId가 null/blank -> 401 Unauthorized. 인증 헤더 필수값 검증 실패")
		void issueQueueToken_invalidLoginId_returns401(String loginId) throws Exception {
			// Act & Assert
			var requestBuilder = post("/auth/queue-token")
				.header("X-Loopers-LoginPw", "Test1234!");

			if (loginId != null) {
				requestBuilder = requestBuilder.header("X-Loopers-LoginId", loginId);
			}

			mockMvc.perform(requestBuilder)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}


		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {"  ", "\t"})
		@DisplayName("[POST /auth/queue-token] LoginPw가 null/blank -> 401 Unauthorized. 인증 헤더 필수값 검증 실패")
		void issueQueueToken_invalidLoginPw_returns401(String password) throws Exception {
			// Act & Assert
			var requestBuilder = post("/auth/queue-token")
				.header("X-Loopers-LoginId", "testuser01");

			if (password != null) {
				requestBuilder = requestBuilder.header("X-Loopers-LoginPw", password);
			}

			mockMvc.perform(requestBuilder)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}
	}
}
