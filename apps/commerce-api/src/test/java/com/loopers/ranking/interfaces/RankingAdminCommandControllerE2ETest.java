package com.loopers.ranking.interfaces;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.ranking.interfaces.web.request.AdminRankingRebuildRequest;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * 랭킹 관리자 명령 E2E 테스트
 * - POST /api-admin/v1/rankings/rebuild
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("RankingAdminCommandController E2E 테스트")
class RankingAdminCommandControllerE2ETest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	@Autowired
	private RedisCleanUp redisCleanUp;

	private static final String ADMIN_LDAP_HEADER = "X-Loopers-Ldap";
	private static final String ADMIN_LDAP_VALUE = "loopers.admin";


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
		redisCleanUp.truncateAll();
	}


	@Nested
	@DisplayName("POST /api-admin/v1/rankings/rebuild - 랭킹 재계산 요청")
	class RequestRebuildTest {

		@Test
		@DisplayName("[POST /api-admin/v1/rankings/rebuild] 유효한 재계산 요청 -> 202 Accepted. status=ACCEPTED, from/to/scorerType/carryOverWeight 포함")
		void rebuildAccepted() throws Exception {
			// Arrange
			AdminRankingRebuildRequest request = new AdminRankingRebuildRequest(
				"20250101", "20250107", "SATURATION", 0.1
			);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/rankings/rebuild")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("ACCEPTED"))
				.andExpect(jsonPath("$.from").value("20250101"))
				.andExpect(jsonPath("$.to").value("20250107"))
				.andExpect(jsonPath("$.scorerType").value("SATURATION"))
				.andExpect(jsonPath("$.carryOverWeight").value(0.1));
		}


		@Test
		@DisplayName("[POST /api-admin/v1/rankings/rebuild] scorerType/carryOverWeight 미입력 -> 202 Accepted. 기본값(SATURATION/0.1) 적용")
		void rebuildWithDefaults() throws Exception {
			// Arrange — scorerType, carryOverWeight null
			AdminRankingRebuildRequest request = new AdminRankingRebuildRequest(
				"20250101", "20250107", null, null
			);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/rankings/rebuild")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("ACCEPTED"))
				.andExpect(jsonPath("$.scorerType").value("SATURATION"))
				.andExpect(jsonPath("$.carryOverWeight").value(0.1));
		}


		@Test
		@DisplayName("[POST /api-admin/v1/rankings/rebuild] from이 to보다 늦은 날짜 -> 400 Bad Request")
		void rebuildWithInvalidDateRange() throws Exception {
			// Arrange — from > to
			AdminRankingRebuildRequest request = new AdminRankingRebuildRequest(
				"20250107", "20250101", "SATURATION", 0.1
			);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/rankings/rebuild")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[POST /api-admin/v1/rankings/rebuild] 날짜 형식 오류 (yyyyMMdd 아님) -> 400 Bad Request")
		void rebuildWithInvalidDateFormat() throws Exception {
			// Arrange — 잘못된 날짜 형식
			AdminRankingRebuildRequest request = new AdminRankingRebuildRequest(
				"2025-01-01", "2025-01-07", "SATURATION", 0.1
			);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/rankings/rebuild")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());
		}


		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {"  ", "\t"})
		@DisplayName("[POST /api-admin/v1/rankings/rebuild] LDAP 헤더 누락/공백 -> 401 Unauthorized")
		void rebuildWithoutAuth(String ldapHeader) throws Exception {
			// Arrange
			AdminRankingRebuildRequest request = new AdminRankingRebuildRequest(
				"20250101", "20250107", "SATURATION", 0.1
			);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/rankings/rebuild")
					.header(ADMIN_LDAP_HEADER, ldapHeader != null ? ldapHeader : "")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized());
		}


		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {"  "})
		@DisplayName("[POST /api-admin/v1/rankings/rebuild] from 미입력 -> 400 Bad Request")
		void rebuildWithBlankFrom(String from) throws Exception {
			// Arrange
			AdminRankingRebuildRequest request = new AdminRankingRebuildRequest(
				from, "20250107", "SATURATION", 0.1
			);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/rankings/rebuild")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());
		}


		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {"  "})
		@DisplayName("[POST /api-admin/v1/rankings/rebuild] to 미입력 -> 400 Bad Request")
		void rebuildWithBlankTo(String to) throws Exception {
			// Arrange
			AdminRankingRebuildRequest request = new AdminRankingRebuildRequest(
				"20250101", to, "SATURATION", 0.1
			);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/rankings/rebuild")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[POST /api-admin/v1/rankings/rebuild] from = to (단일 날짜) -> 202 Accepted")
		void rebuildSingleDay() throws Exception {
			// Arrange
			AdminRankingRebuildRequest request = new AdminRankingRebuildRequest(
				"20250101", "20250101", "SATURATION", 0.1
			);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/rankings/rebuild")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("ACCEPTED"))
				.andExpect(jsonPath("$.from").value("20250101"))
				.andExpect(jsonPath("$.to").value("20250101"));
		}
	}

}
