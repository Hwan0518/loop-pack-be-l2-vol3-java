package com.loopers.catalog.brand.interfaces;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.catalog.brand.interfaces.web.request.AdminBrandCreateRequest;
import com.loopers.catalog.brand.interfaces.web.request.AdminBrandUpdateRequest;
import com.loopers.catalog.brand.interfaces.web.request.AdminBrandVisibleStatusUpdateRequest;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.support.common.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("BrandController E2E 테스트")
class BrandControllerE2ETest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	private static final String ADMIN_LDAP_HEADER = "X-Loopers-Ldap";
	private static final String ADMIN_LDAP_VALUE = "loopers.admin";


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Nested
	@DisplayName("GET /api/v1/brands - 브랜드 목록 조회 (공개, VISIBLE만)")
	class GetBrandsTest {

		@Test
		@DisplayName("[GET /api/v1/brands] 브랜드 없음 -> 200 OK. 빈 목록 반환")
		void getBrandsEmpty() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/brands"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.content", hasSize(0)))
				.andExpect(jsonPath("$.totalElements").value(0));
		}


		@Test
		@DisplayName("[GET /api/v1/brands] HIDDEN 브랜드만 존재 -> 200 OK. 빈 목록 반환 (HIDDEN 미노출)")
		void getBrandsHiddenNotShown() throws Exception {
			// Arrange
			createBrand("나이키", "스포츠 브랜드");
			createBrand("아디다스", "독일 스포츠 브랜드");

			// Act & Assert
			mockMvc.perform(get("/api/v1/brands"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(0)))
				.andExpect(jsonPath("$.totalElements").value(0));
		}


		@Test
		@DisplayName("[GET /api/v1/brands] VISIBLE 설정 후 조회 -> 200 OK. VISIBLE 브랜드만 반환")
		void getBrandsOnlyVisible() throws Exception {
			// Arrange
			Long brandId1 = createBrandAndGetId("나이키", "스포츠 브랜드");
			createBrand("아디다스", "독일 스포츠 브랜드");
			updateVisibleStatus(brandId1, VisibleStatus.VISIBLE);

			// Act & Assert
			mockMvc.perform(get("/api/v1/brands"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].name").value("나이키"))
				.andExpect(jsonPath("$.totalElements").value(1));
		}


		@Test
		@DisplayName("[GET /api/v1/brands] 3개 VISIBLE 브랜드, 페이지 크기 2 -> 200 OK. 2개 반환, totalElements=3")
		void getBrandsWithPagination() throws Exception {
			// Arrange
			Long brandId1 = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long brandId2 = createBrandAndGetId("아디다스", "독일 스포츠 브랜드");
			Long brandId3 = createBrandAndGetId("뉴발란스", "미국 스포츠 브랜드");
			updateVisibleStatus(brandId1, VisibleStatus.VISIBLE);
			updateVisibleStatus(brandId2, VisibleStatus.VISIBLE);
			updateVisibleStatus(brandId3, VisibleStatus.VISIBLE);

			// Act & Assert
			mockMvc.perform(get("/api/v1/brands")
					.param("page", "0")
					.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.totalElements").value(3));
		}


		@Test
		@DisplayName("[GET /api/v1/brands] 인증 없이 조회 가능 -> 200 OK")
		void getBrandsWithoutAuth() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/brands"))
				.andExpect(status().isOk());
		}

	}


	@Nested
	@DisplayName("GET /api/v1/brands/{brandId} - 브랜드 상세 조회 (공개, VISIBLE만)")
	class GetBrandTest {

		@Test
		@DisplayName("[GET /api/v1/brands/{brandId}] VISIBLE 브랜드 -> 200 OK. id, name, description 포함")
		void getBrandSuccess() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			updateVisibleStatus(brandId, VisibleStatus.VISIBLE);

			// Act & Assert
			mockMvc.perform(get("/api/v1/brands/{brandId}", brandId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(brandId))
				.andExpect(jsonPath("$.name").value("나이키"))
				.andExpect(jsonPath("$.description").value("스포츠 브랜드"));
		}


		@Test
		@DisplayName("[GET /api/v1/brands/{brandId}] HIDDEN 브랜드 -> 404 Not Found. BRAND_NOT_FOUND")
		void getBrandHiddenNotFound() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");

			// Act & Assert
			mockMvc.perform(get("/api/v1/brands/{brandId}", brandId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.BRAND_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[GET /api/v1/brands/{brandId}] 존재하지 않는 ID -> 404 Not Found. BRAND_NOT_FOUND")
		void getBrandNotFound() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/brands/{brandId}", 999L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.BRAND_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[GET /api/v1/brands/{brandId}] 삭제된 브랜드 ID -> 404 Not Found. BRAND_NOT_FOUND")
		void getBrandSoftDeleted() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			updateVisibleStatus(brandId, VisibleStatus.HIDDEN);
			deleteBrand(brandId);

			// Act & Assert
			mockMvc.perform(get("/api/v1/brands/{brandId}", brandId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.BRAND_NOT_FOUND.getCode()));
		}

	}


	@Nested
	@DisplayName("GET /api-admin/v1/brands - 브랜드 목록 조회 (관리자)")
	class GetAdminBrandsTest {

		@Test
		@DisplayName("[GET /api-admin/v1/brands] LDAP 인증 성공, 필터 없음 -> 200 OK. 전체 브랜드 반환")
		void getAdminBrandsAll() throws Exception {
			// Arrange
			Long brandId1 = createBrandAndGetId("나이키", "스포츠 브랜드");
			createBrand("아디다스", "독일 스포츠 브랜드");
			updateVisibleStatus(brandId1, VisibleStatus.VISIBLE);

			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/brands")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.totalElements").value(2));
		}


		@Test
		@DisplayName("[GET /api-admin/v1/brands] visibleStatus=VISIBLE 필터 -> 200 OK. VISIBLE 브랜드만 반환")
		void getAdminBrandsVisibleFilter() throws Exception {
			// Arrange
			Long brandId1 = createBrandAndGetId("나이키", "스포츠 브랜드");
			createBrand("아디다스", "독일 스포츠 브랜드");
			updateVisibleStatus(brandId1, VisibleStatus.VISIBLE);

			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/brands")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.param("visibleStatus", "VISIBLE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].name").value("나이키"))
				.andExpect(jsonPath("$.content[0].visibleStatus").value("VISIBLE"))
				.andExpect(jsonPath("$.totalElements").value(1));
		}


		@Test
		@DisplayName("[GET /api-admin/v1/brands] visibleStatus=HIDDEN 필터 -> 200 OK. HIDDEN 브랜드만 반환")
		void getAdminBrandsHiddenFilter() throws Exception {
			// Arrange
			Long brandId1 = createBrandAndGetId("나이키", "스포츠 브랜드");
			createBrand("아디다스", "독일 스포츠 브랜드");
			updateVisibleStatus(brandId1, VisibleStatus.VISIBLE);

			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/brands")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.param("visibleStatus", "HIDDEN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].name").value("아디다스"))
				.andExpect(jsonPath("$.content[0].visibleStatus").value("HIDDEN"))
				.andExpect(jsonPath("$.totalElements").value(1));
		}


		@Test
		@DisplayName("[GET /api-admin/v1/brands] LDAP 헤더 누락 -> 401 Unauthorized")
		void getAdminBrandsUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/brands"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}

	}


	@Nested
	@DisplayName("GET /api-admin/v1/brands/{brandId} - 브랜드 상세 조회 (관리자)")
	class GetAdminBrandTest {

		@Test
		@DisplayName("[GET /api-admin/v1/brands/{brandId}] HIDDEN 브랜드도 조회 가능 -> 200 OK. visibleStatus 포함")
		void getAdminBrandHidden() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");

			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/brands/{brandId}", brandId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(brandId))
				.andExpect(jsonPath("$.name").value("나이키"))
				.andExpect(jsonPath("$.description").value("스포츠 브랜드"))
				.andExpect(jsonPath("$.visibleStatus").value("HIDDEN"));
		}


		@Test
		@DisplayName("[GET /api-admin/v1/brands/{brandId}] VISIBLE 브랜드 조회 -> 200 OK")
		void getAdminBrandVisible() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			updateVisibleStatus(brandId, VisibleStatus.VISIBLE);

			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/brands/{brandId}", brandId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.visibleStatus").value("VISIBLE"));
		}


		@Test
		@DisplayName("[GET /api-admin/v1/brands/{brandId}] 존재하지 않는 ID -> 404 Not Found")
		void getAdminBrandNotFound() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/brands/{brandId}", 999L)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.BRAND_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[GET /api-admin/v1/brands/{brandId}] LDAP 헤더 누락 -> 401 Unauthorized")
		void getAdminBrandUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api-admin/v1/brands/{brandId}", 1L))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}

	}


	@Nested
	@DisplayName("POST /api-admin/v1/brands - 브랜드 생성")
	class CreateBrandTest {

		@Test
		@DisplayName("[POST /api-admin/v1/brands] 유효한 요청 -> 201 Created. visibleStatus=HIDDEN, id, name, description 포함")
		void createBrandSuccess() throws Exception {
			// Arrange
			AdminBrandCreateRequest request = new AdminBrandCreateRequest("나이키", "스포츠 브랜드");

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/brands")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value("나이키"))
				.andExpect(jsonPath("$.description").value("스포츠 브랜드"))
				.andExpect(jsonPath("$.visibleStatus").value("HIDDEN"));
		}


		@Test
		@DisplayName("[POST /api-admin/v1/brands] LDAP 헤더 누락 -> 401 Unauthorized")
		void createBrandUnauthorized() throws Exception {
			// Arrange
			AdminBrandCreateRequest request = new AdminBrandCreateRequest("나이키", "스포츠 브랜드");

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/brands")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}


		@Test
		@DisplayName("[POST /api-admin/v1/brands] name 누락 -> 400 Bad Request")
		void createBrandFailNameMissing() throws Exception {
			// Arrange
			String requestJson = """
			                     {
			                     	"description": "스포츠 브랜드"
			                     }
			                     """;

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/brands")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[POST /api-admin/v1/brands] name이 100자 초과 -> 400 Bad Request. INVALID_BRAND_NAME")
		void createBrandFailNameTooLong() throws Exception {
			// Arrange
			String longName = "가".repeat(101);
			AdminBrandCreateRequest request = new AdminBrandCreateRequest(longName, "설명");

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/brands")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_BRAND_NAME.getCode()));
		}


		@Test
		@DisplayName("[POST /api-admin/v1/brands] description null -> 201 Created. description=null")
		void createBrandWithNullDescription() throws Exception {
			// Arrange
			AdminBrandCreateRequest request = new AdminBrandCreateRequest("나이키", null);

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/brands")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("나이키"))
				.andExpect(jsonPath("$.description").doesNotExist())
				.andExpect(jsonPath("$.visibleStatus").value("HIDDEN"));
		}


		@Test
		@DisplayName("[POST /api-admin/v1/brands] name 앞뒤 공백 -> trim 처리 후 201 Created")
		void createBrandTrimsName() throws Exception {
			// Arrange
			AdminBrandCreateRequest request = new AdminBrandCreateRequest("  나이키  ", "스포츠 브랜드");

			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/brands")
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("나이키"));
		}

	}


	@Nested
	@DisplayName("PUT /api-admin/v1/brands/{brandId} - 브랜드 수정")
	class UpdateBrandTest {

		@Test
		@DisplayName("[PUT /api-admin/v1/brands/{brandId}] 유효한 요청 -> 200 OK. 수정된 name, description 반환. visibleStatus 포함")
		void updateBrandSuccess() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			AdminBrandUpdateRequest request = new AdminBrandUpdateRequest("아디다스", "독일 스포츠 브랜드", null);

			// Act & Assert
			mockMvc.perform(put("/api-admin/v1/brands/{brandId}", brandId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(brandId))
				.andExpect(jsonPath("$.name").value("아디다스"))
				.andExpect(jsonPath("$.description").value("독일 스포츠 브랜드"))
				.andExpect(jsonPath("$.visibleStatus").value("HIDDEN"));
		}


		@Test
		@DisplayName("[PUT /api-admin/v1/brands/{brandId}] visibleStatus 포함 수정 -> 200 OK. 노출 상태도 변경")
		void updateBrandWithVisibleStatus() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			AdminBrandUpdateRequest request = new AdminBrandUpdateRequest("나이키", "스포츠 브랜드", VisibleStatus.VISIBLE);

			// Act & Assert
			mockMvc.perform(put("/api-admin/v1/brands/{brandId}", brandId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.visibleStatus").value("VISIBLE"));
		}


		@Test
		@DisplayName("[PUT /api-admin/v1/brands/{brandId}] LDAP 헤더 누락 -> 401 Unauthorized")
		void updateBrandUnauthorized() throws Exception {
			// Arrange
			AdminBrandUpdateRequest request = new AdminBrandUpdateRequest("아디다스", "독일 스포츠 브랜드", null);

			// Act & Assert
			mockMvc.perform(put("/api-admin/v1/brands/{brandId}", 1L)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}


		@Test
		@DisplayName("[PUT /api-admin/v1/brands/{brandId}] 존재하지 않는 ID -> 404 Not Found. BRAND_NOT_FOUND")
		void updateBrandNotFound() throws Exception {
			// Arrange
			AdminBrandUpdateRequest request = new AdminBrandUpdateRequest("아디다스", "독일 스포츠 브랜드", null);

			// Act & Assert
			mockMvc.perform(put("/api-admin/v1/brands/{brandId}", 999L)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.BRAND_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[PUT /api-admin/v1/brands/{brandId}] name 누락 -> 400 Bad Request")
		void updateBrandFailNameMissing() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			String requestJson = """
			                     {
			                     	"description": "새 설명"
			                     }
			                     """;

			// Act & Assert
			mockMvc.perform(put("/api-admin/v1/brands/{brandId}", brandId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest());
		}

	}


	@Nested
	@DisplayName("PATCH /api-admin/v1/brands/{brandId}/visible-status - 브랜드 노출 상태 변경")
	class UpdateVisibleStatusTest {

		@Test
		@DisplayName("[PATCH /api-admin/v1/brands/{brandId}/visible-status] VISIBLE -> 200 OK. visibleStatus=VISIBLE")
		void updateVisibleStatusToVisible() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			AdminBrandVisibleStatusUpdateRequest request = new AdminBrandVisibleStatusUpdateRequest(VisibleStatus.VISIBLE);

			// Act & Assert
			mockMvc.perform(patch("/api-admin/v1/brands/{brandId}/visible-status", brandId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(brandId))
				.andExpect(jsonPath("$.name").value("나이키"))
				.andExpect(jsonPath("$.visibleStatus").value("VISIBLE"));
		}


		@Test
		@DisplayName("[PATCH /api-admin/v1/brands/{brandId}/visible-status] HIDDEN -> 200 OK. visibleStatus=HIDDEN")
		void updateVisibleStatusToHidden() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			updateVisibleStatus(brandId, VisibleStatus.VISIBLE);
			AdminBrandVisibleStatusUpdateRequest request = new AdminBrandVisibleStatusUpdateRequest(VisibleStatus.HIDDEN);

			// Act & Assert
			mockMvc.perform(patch("/api-admin/v1/brands/{brandId}/visible-status", brandId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.visibleStatus").value("HIDDEN"));
		}


		@Test
		@DisplayName("[PATCH /api-admin/v1/brands/{brandId}/visible-status] visibleStatus=null -> 400 Bad Request")
		void updateVisibleStatusNullFails() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			String requestJson = """
			                     {
			                     	"visibleStatus": null
			                     }
			                     """;

			// Act & Assert
			mockMvc.perform(patch("/api-admin/v1/brands/{brandId}/visible-status", brandId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJson))
				.andExpect(status().isBadRequest());
		}


		@Test
		@DisplayName("[PATCH /api-admin/v1/brands/{brandId}/visible-status] LDAP 헤더 누락 -> 401 Unauthorized")
		void updateVisibleStatusUnauthorized() throws Exception {
			// Arrange
			AdminBrandVisibleStatusUpdateRequest request = new AdminBrandVisibleStatusUpdateRequest(VisibleStatus.VISIBLE);

			// Act & Assert
			mockMvc.perform(patch("/api-admin/v1/brands/{brandId}/visible-status", 1L)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}


		@Test
		@DisplayName("[PATCH /api-admin/v1/brands/{brandId}/visible-status] 존재하지 않는 ID -> 404 Not Found")
		void updateVisibleStatusNotFound() throws Exception {
			// Arrange
			AdminBrandVisibleStatusUpdateRequest request = new AdminBrandVisibleStatusUpdateRequest(VisibleStatus.VISIBLE);

			// Act & Assert
			mockMvc.perform(patch("/api-admin/v1/brands/{brandId}/visible-status", 999L)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.BRAND_NOT_FOUND.getCode()));
		}

	}


	@Nested
	@DisplayName("DELETE /api-admin/v1/brands/{brandId} - 브랜드 삭제")
	class DeleteBrandTest {

		@Test
		@DisplayName("[DELETE /api-admin/v1/brands/{brandId}] 유효한 요청 -> 204 No Content")
		void deleteBrandSuccess() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");

			// Act & Assert
			mockMvc.perform(delete("/api-admin/v1/brands/{brandId}", brandId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isNoContent());
		}


		@Test
		@DisplayName("[DELETE /api-admin/v1/brands/{brandId}] 삭제 후 조회 -> 404 Not Found. BRAND_NOT_FOUND")
		void deleteBrandThenGetReturns404() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			updateVisibleStatus(brandId, VisibleStatus.HIDDEN);
			deleteBrand(brandId);

			// Act & Assert
			mockMvc.perform(get("/api/v1/brands/{brandId}", brandId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.BRAND_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[DELETE /api-admin/v1/brands/{brandId}] LDAP 헤더 누락 -> 401 Unauthorized")
		void deleteBrandUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(delete("/api-admin/v1/brands/{brandId}", 1L))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}


		@Test
		@DisplayName("[DELETE /api-admin/v1/brands/{brandId}] VISIBLE 브랜드 삭제 -> 409 Conflict. BRAND_IS_VISIBLE")
		void deleteBrandFailWhenVisible() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			updateVisibleStatus(brandId, VisibleStatus.VISIBLE);

			// Act & Assert
			mockMvc.perform(delete("/api-admin/v1/brands/{brandId}", brandId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(ErrorType.BRAND_IS_VISIBLE.getCode()));
		}


		@Test
		@DisplayName("[DELETE /api-admin/v1/brands/{brandId}] 존재하지 않는 ID -> 404 Not Found. BRAND_NOT_FOUND")
		void deleteBrandNotFound() throws Exception {
			// Act & Assert
			mockMvc.perform(delete("/api-admin/v1/brands/{brandId}", 999L)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.BRAND_NOT_FOUND.getCode()));
		}

	}


	/**
	 * 테스트 헬퍼 메서드
	 */

	private void createBrand(String name, String description) throws Exception {
		AdminBrandCreateRequest request = new AdminBrandCreateRequest(name, description);
		mockMvc.perform(post("/api-admin/v1/brands")
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}


	private Long createBrandAndGetId(String name, String description) throws Exception {
		AdminBrandCreateRequest request = new AdminBrandCreateRequest(name, description);
		MvcResult result = mockMvc.perform(post("/api-admin/v1/brands")
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}


	private void updateVisibleStatus(Long brandId, VisibleStatus visibleStatus) throws Exception {
		AdminBrandVisibleStatusUpdateRequest request = new AdminBrandVisibleStatusUpdateRequest(visibleStatus);
		mockMvc.perform(patch("/api-admin/v1/brands/{brandId}/visible-status", brandId)
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());
	}


	private void deleteBrand(Long brandId) throws Exception {
		mockMvc.perform(delete("/api-admin/v1/brands/{brandId}", brandId)
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
			.andExpect(status().isNoContent());
	}

}
