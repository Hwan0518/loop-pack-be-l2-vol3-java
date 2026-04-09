package com.loopers.ranking.interfaces;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.interfaces.web.request.AdminBrandCreateRequest;
import com.loopers.catalog.product.interfaces.web.request.AdminProductCreateRequest;
import com.loopers.config.redis.RedisConfig;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * 랭킹 조회 E2E 테스트
 * - Redis ZSET에 직접 데이터 적재 후 API 조회 검증
 * - 상품 상세 조회 시 rank 포함 검증
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("RankingQueryController E2E 테스트")
class RankingQueryControllerE2ETest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	@Autowired
	private RedisCleanUp redisCleanUp;

	@Autowired
	@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
	private RedisTemplate<String, String> redisTemplate;

	private static final String ADMIN_LDAP_HEADER = "X-Loopers-Ldap";
	private static final String ADMIN_LDAP_VALUE = "loopers.admin";
	private static final String TODAY = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
	private static final String ZSET_KEY = "ranking:all:" + TODAY;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
		redisCleanUp.truncateAll();
	}


	@Nested
	@DisplayName("GET /api/v1/rankings - 랭킹 목록 조회")
	class GetRankingsTest {

		@Test
		@DisplayName("[GET /api/v1/rankings] ZSET 비어있음 -> 200 OK. 빈 목록, totalElements=0")
		void emptyRankings() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/rankings"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.content", hasSize(0)))
				.andExpect(jsonPath("$.totalElements").value(0));
		}


		@Test
		@DisplayName("[GET /api/v1/rankings] 상품 2개 랭킹 등록 -> 200 OK. 점수 높은 순서로 정렬, 상품 정보 포함")
		void rankingsWithProducts() throws Exception {
			// Arrange — 상품 생성
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId1 = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");
			Long productId2 = createProductAndGetId(brandId, "에어포스", new BigDecimal("139000"), 50L, "캐주얼화");

			// Arrange — Redis ZSET 직접 적재
			redisTemplate.opsForZSet().add(ZSET_KEY, productId1.toString(), 0.8);
			redisTemplate.opsForZSet().add(ZSET_KEY, productId2.toString(), 0.6);

			// Act & Assert
			mockMvc.perform(get("/api/v1/rankings"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.content[0].rank").value(1))
				.andExpect(jsonPath("$.content[0].productId").value(productId1))
				.andExpect(jsonPath("$.content[0].name").value("에어맥스"))
				.andExpect(jsonPath("$.content[0].brandName").value("나이키"))
				.andExpect(jsonPath("$.content[0].price").value(129000))
				.andExpect(jsonPath("$.content[1].rank").value(2))
				.andExpect(jsonPath("$.content[1].productId").value(productId2))
				.andExpect(jsonPath("$.totalElements").value(2));
		}


		@Test
		@DisplayName("[GET /api/v1/rankings] 페이지네이션 -> page=0, size=1이면 1개만 반환, totalElements=2")
		void rankingsWithPagination() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId1 = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");
			Long productId2 = createProductAndGetId(brandId, "에어포스", new BigDecimal("139000"), 50L, "캐주얼화");

			redisTemplate.opsForZSet().add(ZSET_KEY, productId1.toString(), 0.8);
			redisTemplate.opsForZSet().add(ZSET_KEY, productId2.toString(), 0.6);

			// Act & Assert
			mockMvc.perform(get("/api/v1/rankings")
					.param("page", "0")
					.param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].rank").value(1))
				.andExpect(jsonPath("$.totalElements").value(2));
		}


		@Test
		@DisplayName("[GET /api/v1/rankings] date 파라미터 지정 -> 해당 날짜의 ZSET 조회")
		void rankingsWithDateParam() throws Exception {
			// Arrange — 어제 날짜 ZSET 적재
			String yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");

			redisTemplate.opsForZSet().add("ranking:all:" + yesterday, productId.toString(), 0.5);

			// Act & Assert — 어제 날짜로 조회
			mockMvc.perform(get("/api/v1/rankings")
					.param("date", yesterday))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].productId").value(productId));

			// 오늘 날짜로 조회하면 비어있음
			mockMvc.perform(get("/api/v1/rankings"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(0)));
		}


		@Test
		@DisplayName("[GET /api/v1/rankings] 인증 없이 조회 가능 -> 200 OK")
		void rankingsWithoutAuth() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/rankings"))
				.andExpect(status().isOk());
		}


		@Test
		@DisplayName("[GET /api/v1/rankings] page=-1, size=0 -> 200 OK. 입력 보정 (page=0, size=1)")
		void rankingsWithInvalidParams() throws Exception {
			// Act & Assert — 음수 page, 0 size가 보정되어 정상 응답
			mockMvc.perform(get("/api/v1/rankings")
					.param("page", "-1")
					.param("size", "0"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(1));
		}


		@Test
		@DisplayName("[GET /api/v1/rankings] size=200 -> 200 OK. 최대 100으로 보정")
		void rankingsWithOversizedParam() throws Exception {
			// Act & Assert — size 100 초과 시 100으로 보정
			mockMvc.perform(get("/api/v1/rankings")
					.param("size", "200"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(100));
		}
	}


	@Nested
	@DisplayName("GET /api/v1/products/{productId} - 상품 상세 조회 시 rank 포함")
	class GetProductWithRankTest {

		@Test
		@DisplayName("[GET /api/v1/products/{productId}] 랭킹 등록 상품 -> 200 OK. rank 필드 포함")
		void productWithRank() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");

			// ZSET에 해당 상품 + 다른 상품 적재 (rank=2 되도록)
			redisTemplate.opsForZSet().add(ZSET_KEY, "999", 0.9); // 1위: 존재하지 않는 상품
			redisTemplate.opsForZSet().add(ZSET_KEY, productId.toString(), 0.5); // 2위

			// Act & Assert
			mockMvc.perform(get("/api/v1/products/{productId}", productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(productId))
				.andExpect(jsonPath("$.name").value("에어맥스"))
				.andExpect(jsonPath("$.rank").value(2));
		}


		@Test
		@DisplayName("[GET /api/v1/products/{productId}] 랭킹 미등록 상품 -> 200 OK. rank=null")
		void productWithoutRank() throws Exception {
			// Arrange
			Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
			Long productId = createProductAndGetId(brandId, "에어맥스", new BigDecimal("129000"), 100L, "러닝화");

			// ZSET에 해당 상품 미적재

			// Act & Assert
			mockMvc.perform(get("/api/v1/products/{productId}", productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(productId))
				.andExpect(jsonPath("$.rank").doesNotExist()); // null이면 JSON에서 제외
		}
	}


	/**
	 * 테스트 헬퍼 메서드
	 */

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


	private Long createProductAndGetId(Long brandId, String name, BigDecimal price, Long stock, String description) throws Exception {
		AdminProductCreateRequest request = new AdminProductCreateRequest(brandId, name, price, stock, description);
		MvcResult result = mockMvc.perform(post("/api-admin/v1/products")
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

}
