package com.loopers.catalog.product.infrastructure;


import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;


/**
 * 상품 API 레벨 성능 측정 (NO-CACHE / CACHE)
 * - MockMvc를 통해 실제 Controller → Facade → Service → Repository → DB 전체 스택 측정
 * - NO-CACHE: 인덱스만 적용, Redis 캐시 미적용 상태의 기준선
 * - CACHE (TO-BE): 인덱스 + Redis 캐시 적용 상태 (캐시 히트/미스 분리 측정)
 * - 측정 축: 데이터 규모(10만/100만/1000만) × 트래픽 유형(단일쿼리/버스트/지속부하)
 *
 * 1. 단일 쿼리: 목록/상세 API (warmup 3회 + 측정 5회 평균)
 * 2. 버스트: 100 concurrent 동시 요청 → p50/p95/p99
 * 3. 지속 부하: 20 RPS × 10초 → 처리량 + p50/p95/p99
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("상품 API 성능 측정")
class ProductApiPerformanceTest {

	private static final Logger log = LoggerFactory.getLogger(ProductApiPerformanceTest.class);
	private static final String RESULT_FILE = "/tmp/api-perf-results.txt";
	private static final int BRAND_COUNT = 50;

	// 버스트 파라미터
	private static final int BURST_THREADS = 100;

	// 지속 부하 파라미터
	private static final int SUSTAINED_RPS = 20;
	private static final int SUSTAINED_DURATION_SEC = 10;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	@Autowired
	private RedisCleanUp redisCleanUp;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
		redisCleanUp.truncateAll();
	}


	// --- 테스트 메서드 (데이터 규모별) ---

	@Test
	@Timeout(value = 5, unit = TimeUnit.MINUTES)
	@DisplayName("[NO-CACHE API] 10만건 성능 측정 (인덱스만, 캐시 미적용)")
	void measureApiNoCache_100K() throws Exception {
		runMeasurement(100_000, "100K", 1_000);
	}


	@Test
	@Timeout(value = 15, unit = TimeUnit.MINUTES)
	@DisplayName("[NO-CACHE API] 100만건 성능 측정 (인덱스만, 캐시 미적용)")
	void measureApiNoCache_1M() throws Exception {
		runMeasurement(1_000_000, "1M", 5_000);
	}


	@Test
	@Timeout(value = 30, unit = TimeUnit.MINUTES)
	@DisplayName("[NO-CACHE API] 1000만건 성능 측정 (인덱스만, 캐시 미적용)")
	void measureApiNoCache_10M() throws Exception {
		runMeasurement(10_000_000, "10M", 10_000);
	}


	// --- CACHE 테스트 메서드 (인덱스 + 캐시 적용, 데이터 규모별) ---

	@Test
	@Timeout(value = 5, unit = TimeUnit.MINUTES)
	@DisplayName("[CACHE API] 10만건 성능 측정 (인덱스 + 캐시 적용)")
	void measureApiCache_100K() throws Exception {
		runToBeMeasurement(100_000, "100K", 1_000);
	}


	@Test
	@Timeout(value = 15, unit = TimeUnit.MINUTES)
	@DisplayName("[CACHE API] 100만건 성능 측정 (인덱스 + 캐시 적용)")
	void measureApiCache_1M() throws Exception {
		runToBeMeasurement(1_000_000, "1M", 5_000);
	}


	@Test
	@Timeout(value = 30, unit = TimeUnit.MINUTES)
	@DisplayName("[CACHE API] 1000만건 성능 측정 (인덱스 + 캐시 적용)")
	void measureApiCache_10M() throws Exception {
		runToBeMeasurement(10_000_000, "10M", 10_000);
	}


	// --- 핵심 측정 흐름 ---

	private void runMeasurement(int productCount, String label, int batchSize) throws Exception {
		// 결과 파일 초기화
		try (PrintWriter pw = new PrintWriter(new FileWriter(RESULT_FILE, false))) {
			pw.println("=== NO-CACHE API Performance Results ===");
		} catch (IOException e) {
			// 무시
		}

		out("\n========================================");
		out(String.format("[%s] NO-CACHE API 성능 측정 시작 (인덱스만, 캐시 미적용, 브랜드 %d개, 상품 %d건)", label, BRAND_COUNT, productCount));
		out("========================================");

		// 1. 데이터 준비 (products + product_read_model — 앱 코드가 read_model 조회)
		long insertStart = System.currentTimeMillis();
		insertBulkData(productCount, batchSize);
		insertReadModelFromProducts();
		long insertElapsed = System.currentTimeMillis() - insertStart;
		out(String.format("[%s] 데이터 삽입 완료: %dms", label, insertElapsed));

		// ANALYZE TABLE로 MySQL 통계 업데이트
		try (Connection conn = dataSource.getConnection();
			 Statement stmt = conn.createStatement()) {
			stmt.execute("ANALYZE TABLE products");
			stmt.execute("ANALYZE TABLE brands");
			stmt.execute("ANALYZE TABLE product_read_model");
		}

		// Redis 초기화 (캐시 미적용 상태 보장)
		redisCleanUp.truncateAll();

		// 2. API 엔드포인트 정의
		// 목록 API — 6개 유즈케이스
		String[][] listApis = {
			{"목록 UC1: brandId=X, LATEST", "/api/v1/products?sort=LATEST&page=0&size=20"},
			{"목록 UC2: brandId=X, PRICE_ASC", "/api/v1/products?sort=PRICE_ASC&page=0&size=20"},
			{"목록 UC3: brandId=X, LIKES_DESC", "/api/v1/products?sort=LIKES_DESC&page=0&size=20"},
			{"목록 UC4: brandId=1, LATEST", "/api/v1/products?brandId=1&sort=LATEST&page=0&size=20"},
			{"목록 UC5: brandId=1, PRICE_ASC", "/api/v1/products?brandId=1&sort=PRICE_ASC&page=0&size=20"},
			{"목록 UC6: brandId=1, LIKES_DESC", "/api/v1/products?brandId=1&sort=LIKES_DESC&page=0&size=20"},
		};

		// 상세 API — 상품 ID 1번 (확실히 존재하는 데이터)
		String[][] detailApis = {
			{"상세: productId=1", "/api/v1/products/1"},
		};

		// 3. 단일 쿼리 측정 (매 요청마다 Redis 초기화 — 캐시 미적용 보장)
		out(String.format("\n[%s] ===== NO-CACHE API 단일 쿼리 측정 =====", label));
		for (String[] api : listApis) {
			measureSingleApiNoCache(label + " NO-CACHE", api[0], api[1]);
		}
		for (String[] api : detailApis) {
			measureSingleApiNoCache(label + " NO-CACHE", api[0], api[1]);
		}

		// 4. 버스트 측정 (대표 UC: UC1, UC3, UC4, 상세)
		out(String.format("\n[%s] ===== NO-CACHE API 버스트 측정 (%d건 동시) =====", label, BURST_THREADS));
		redisCleanUp.truncateAll();
		measureBurst(label + " NO-CACHE", listApis[0][0], listApis[0][1]);
		redisCleanUp.truncateAll();
		measureBurst(label + " NO-CACHE", listApis[2][0], listApis[2][1]);
		redisCleanUp.truncateAll();
		measureBurst(label + " NO-CACHE", listApis[3][0], listApis[3][1]);
		redisCleanUp.truncateAll();
		measureBurst(label + " NO-CACHE", detailApis[0][0], detailApis[0][1]);

		// 5. 지속 부하 측정 (대표 UC)
		out(String.format("\n[%s] ===== NO-CACHE API 지속 부하 측정 (%d RPS × %d초) =====", label, SUSTAINED_RPS, SUSTAINED_DURATION_SEC));
		redisCleanUp.truncateAll();
		measureSustainedLoad(label + " NO-CACHE", listApis[0][0], listApis[0][1]);
		redisCleanUp.truncateAll();
		measureSustainedLoad(label + " NO-CACHE", listApis[2][0], listApis[2][1]);
		redisCleanUp.truncateAll();
		measureSustainedLoad(label + " NO-CACHE", listApis[3][0], listApis[3][1]);
		redisCleanUp.truncateAll();
		measureSustainedLoad(label + " NO-CACHE", detailApis[0][0], detailApis[0][1]);

		out(String.format("\n[%s] ===== NO-CACHE API 측정 완료 =====", label));
	}


	// --- CACHE 핵심 측정 흐름 (인덱스 + 캐시 적용) ---

	private void runToBeMeasurement(int productCount, String label, int batchSize) throws Exception {
		// 결과 파일 초기화
		try (PrintWriter pw = new PrintWriter(new FileWriter(RESULT_FILE, false))) {
			pw.println("=== CACHE API Performance Results ===");
		} catch (IOException e) {
			// 무시
		}

		out("\n========================================");
		out(String.format("[%s] CACHE API 성능 측정 시작 (인덱스 + 캐시 적용, 브랜드 %d개, 상품 %d건)", label, BRAND_COUNT, productCount));
		out("========================================");

		// 1. 데이터 준비 (products + product_read_model)
		long insertStart = System.currentTimeMillis();
		insertBulkData(productCount, batchSize);
		insertReadModelFromProducts();
		long insertElapsed = System.currentTimeMillis() - insertStart;
		out(String.format("[%s] 데이터 삽입 완료: %dms", label, insertElapsed));

		// ANALYZE TABLE로 MySQL 통계 업데이트
		try (Connection conn = dataSource.getConnection();
			 Statement stmt = conn.createStatement()) {
			stmt.execute("ANALYZE TABLE products");
			stmt.execute("ANALYZE TABLE brands");
			stmt.execute("ANALYZE TABLE product_read_model");
		}

		// 2. API 엔드포인트 정의 (NO-CACHE와 동일)
		String[][] listApis = {
			{"목록 UC1: brandId=X, LATEST", "/api/v1/products?sort=LATEST&page=0&size=20"},
			{"목록 UC2: brandId=X, PRICE_ASC", "/api/v1/products?sort=PRICE_ASC&page=0&size=20"},
			{"목록 UC3: brandId=X, LIKES_DESC", "/api/v1/products?sort=LIKES_DESC&page=0&size=20"},
			{"목록 UC4: brandId=1, LATEST", "/api/v1/products?brandId=1&sort=LATEST&page=0&size=20"},
			{"목록 UC5: brandId=1, PRICE_ASC", "/api/v1/products?brandId=1&sort=PRICE_ASC&page=0&size=20"},
			{"목록 UC6: brandId=1, LIKES_DESC", "/api/v1/products?brandId=1&sort=LIKES_DESC&page=0&size=20"},
		};
		String[][] detailApis = {
			{"상세: productId=1", "/api/v1/products/1"},
		};

		// 3. 캐시 미스 측정 (Redis 비운 상태에서 첫 호출)
		out(String.format("\n[%s] ===== CACHE API 캐시 미스 측정 (첫 호출) =====", label));
		for (String[] api : listApis) {
			measureSingleApiMiss(label + " MISS", api[0], api[1]);
		}
		for (String[] api : detailApis) {
			measureSingleApiMiss(label + " MISS", api[0], api[1]);
		}

		// 4. 캐시 히트 측정 (캐시 워밍 후 반복 호출)
		out(String.format("\n[%s] ===== CACHE API 캐시 히트 측정 (캐시 워밍 후) =====", label));
		for (String[] api : listApis) {
			measureSingleApiHit(label + " HIT", api[0], api[1]);
		}
		for (String[] api : detailApis) {
			measureSingleApiHit(label + " HIT", api[0], api[1]);
		}

		// 5. 버스트 측정 — 캐시 히트 상태에서 (캐시가 이미 워밍된 상태)
		out(String.format("\n[%s] ===== CACHE API 버스트 측정 (캐시 히트, %d건 동시) =====", label, BURST_THREADS));
		measureBurst(label + " HIT", listApis[0][0], listApis[0][1]);
		measureBurst(label + " HIT", listApis[2][0], listApis[2][1]);
		measureBurst(label + " HIT", listApis[3][0], listApis[3][1]);
		measureBurst(label + " HIT", detailApis[0][0], detailApis[0][1]);

		// 6. 버스트 측정 — 캐시 미스 상태에서 (스탬피드 보호 검증)
		out(String.format("\n[%s] ===== CACHE API 버스트 측정 (캐시 미스 — 스탬피드 보호, %d건 동시) =====", label, BURST_THREADS));
		redisCleanUp.truncateAll();
		measureBurst(label + " MISS", listApis[0][0], listApis[0][1]);
		redisCleanUp.truncateAll();
		measureBurst(label + " MISS", detailApis[0][0], detailApis[0][1]);

		// 7. 지속 부하 측정 — 캐시 히트 상태
		out(String.format("\n[%s] ===== CACHE API 지속 부하 측정 (캐시 히트, %d RPS × %d초) =====", label, SUSTAINED_RPS, SUSTAINED_DURATION_SEC));
		// 캐시 워밍 (지속 부하 대표 UC)
		executeSuccessfulRequest(listApis[0][1]);
		executeSuccessfulRequest(listApis[2][1]);
		executeSuccessfulRequest(listApis[3][1]);
		executeSuccessfulRequest(detailApis[0][1]);
		measureSustainedLoad(label + " HIT", listApis[0][0], listApis[0][1]);
		measureSustainedLoad(label + " HIT", listApis[2][0], listApis[2][1]);
		measureSustainedLoad(label + " HIT", listApis[3][0], listApis[3][1]);
		measureSustainedLoad(label + " HIT", detailApis[0][0], detailApis[0][1]);

		out(String.format("\n[%s] ===== CACHE API 측정 완료 =====", label));
	}


	// --- 단일 API 측정 (NO-CACHE: 매 요청마다 Redis 초기화) ---

	private void measureSingleApiNoCache(String dataLabel, String ucLabel, String url) throws Exception {
		// Warmup (3회) — Spring 컨텍스트/JPA 세션/커넥션 풀 안정화
		for (int w = 0; w < 3; w++) {
			executeSuccessfulRequest(url);
		}

		// 실행시간 측정 (5회, 매 요청 전 Redis 초기화)
		int runs = 5;
		long[] times = new long[runs];
		for (int i = 0; i < runs; i++) {
			redisCleanUp.truncateAll();
			times[i] = measureRequestLatency(url);
		}

		long sum = 0, min = Long.MAX_VALUE, max = Long.MIN_VALUE;
		for (long t : times) {
			sum += t;
			min = Math.min(min, t);
			max = Math.max(max, t);
		}

		out(String.format("[%s] [API 단일] %s — avg=%sms, min=%sms, max=%sms",
			dataLabel, ucLabel,
			String.format("%.2f", sum / (double) runs / 1_000_000.0),
			String.format("%.2f", min / 1_000_000.0),
			String.format("%.2f", max / 1_000_000.0)));
	}


	// --- 단일 API 측정 (CACHE: warmup 후 캐시 히트 상태) ---

	private void measureSingleApiHit(String dataLabel, String ucLabel, String url) throws Exception {
		redisCleanUp.truncateAll();
		executeSuccessfulRequest(url);

		for (int w = 0; w < 3; w++) {
			executeSuccessfulRequest(url);
		}

		int runs = 5;
		long[] times = new long[runs];
		for (int i = 0; i < runs; i++) {
			times[i] = measureRequestLatency(url);
		}

		long sum = 0, min = Long.MAX_VALUE, max = Long.MIN_VALUE;
		for (long t : times) {
			sum += t;
			min = Math.min(min, t);
			max = Math.max(max, t);
		}

		out(String.format("[%s] [API 단일] %s — avg=%sms, min=%sms, max=%sms",
			dataLabel, ucLabel,
			String.format("%.2f", sum / (double) runs / 1_000_000.0),
			String.format("%.2f", min / 1_000_000.0),
			String.format("%.2f", max / 1_000_000.0)));
	}


	private void measureSingleApiMiss(String dataLabel, String ucLabel, String url) throws Exception {
		for (int w = 0; w < 3; w++) {
			redisCleanUp.truncateAll();
			executeSuccessfulRequest(url);
		}

		int runs = 5;
		long[] times = new long[runs];
		for (int i = 0; i < runs; i++) {
			redisCleanUp.truncateAll();
			times[i] = measureRequestLatency(url);
		}

		long sum = 0, min = Long.MAX_VALUE, max = Long.MIN_VALUE;
		for (long t : times) {
			sum += t;
			min = Math.min(min, t);
			max = Math.max(max, t);
		}

		out(String.format("[%s] [API 단일] %s — avg=%sms, min=%sms, max=%sms",
			dataLabel, ucLabel,
			String.format("%.2f", sum / (double) runs / 1_000_000.0),
			String.format("%.2f", min / 1_000_000.0),
			String.format("%.2f", max / 1_000_000.0)));
	}


	// --- 버스트 측정 (N개 동시 요청) ---

	private void measureBurst(String dataLabel, String ucLabel, String url) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(BURST_THREADS);
		CountDownLatch ready = new CountDownLatch(BURST_THREADS);
		CountDownLatch go = new CountDownLatch(1);
		long[] latencies = new long[BURST_THREADS];
		AtomicInteger errors = new AtomicInteger(0);

		for (int i = 0; i < BURST_THREADS; i++) {
			final int idx = i;
			executor.submit(() -> {
				ready.countDown();
				try {
					go.await();
					latencies[idx] = measureRequestLatency(url);
				} catch (Exception e) {
					errors.incrementAndGet();
					latencies[idx] = -1;
				}
			});
		}

		ready.await();
		go.countDown();

		executor.shutdown();
		executor.awaitTermination(5, TimeUnit.MINUTES);

		long[] valid = Arrays.stream(latencies).filter(l -> l > 0).sorted().toArray();
		if (valid.length > 0) {
			out(String.format("[%s] [API 버스트] %s — 완료: %d/%d, 에러: %d, avg=%sms, p50=%sms, p95=%sms, p99=%sms, max=%sms",
				dataLabel, ucLabel,
				valid.length, BURST_THREADS, errors.get(),
				String.format("%.2f", avg(valid)),
				String.format("%.2f", percentile(valid, 50)),
				String.format("%.2f", percentile(valid, 95)),
				String.format("%.2f", percentile(valid, 99)),
				String.format("%.2f", valid[valid.length - 1] / 1_000_000.0)));
		} else {
			out(String.format("[%s] [API 버스트] %s — 전체 실패 (에러: %d)", dataLabel, ucLabel, errors.get()));
		}
	}


	// --- 지속 부하 측정 (N RPS × T초) ---

	private void measureSustainedLoad(String dataLabel, String ucLabel, String url) throws Exception {
		int totalRequests = SUSTAINED_RPS * SUSTAINED_DURATION_SEC;
		long intervalMs = 1000 / SUSTAINED_RPS;

		ExecutorService executor = Executors.newFixedThreadPool(SUSTAINED_RPS * 2);
		List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
		AtomicInteger errors = new AtomicInteger(0);

		long testStart = System.nanoTime();

		for (int i = 0; i < totalRequests; i++) {
			executor.submit(() -> {
				long start = System.nanoTime();
				try {
					executeSuccessfulRequest(url);
					latencies.add(System.nanoTime() - start);
				} catch (Exception e) {
					errors.incrementAndGet();
				}
			});

			// Rate limiting
			long elapsed = (System.nanoTime() - testStart) / 1_000_000;
			long expected = (long) (i + 1) * intervalMs;
			long sleepMs = expected - elapsed;
			if (sleepMs > 0) {
				Thread.sleep(sleepMs);
			}
		}

		executor.shutdown();
		boolean finished = executor.awaitTermination(5, TimeUnit.MINUTES);

		long totalTimeMs = (System.nanoTime() - testStart) / 1_000_000;

		long[] sorted = latencies.stream().mapToLong(l -> l).sorted().toArray();
		if (sorted.length > 0) {
			double actualQps = (double) sorted.length / totalTimeMs * 1000;
			out(String.format("[%s] [API 지속부하] %s — 완료: %d/%d, 에러: %d, 실제QPS: %s, avg=%sms, p50=%sms, p95=%sms, p99=%sms, 총시간=%dms%s",
				dataLabel, ucLabel,
				sorted.length, totalRequests, errors.get(),
				String.format("%.1f", actualQps),
				String.format("%.2f", avg(sorted)),
				String.format("%.2f", percentile(sorted, 50)),
				String.format("%.2f", percentile(sorted, 95)),
				String.format("%.2f", percentile(sorted, 99)),
				totalTimeMs,
				finished ? "" : " [TIMEOUT — 일부 미완료]"));
		} else {
			out(String.format("[%s] [API 지속부하] %s — 전체 실패 (에러: %d)", dataLabel, ucLabel, errors.get()));
		}
	}


	private long measureRequestLatency(String url) throws Exception {
		long start = System.nanoTime();
		executeSuccessfulRequest(url);
		return System.nanoTime() - start;
	}


	private void executeSuccessfulRequest(String url) throws Exception {
		MvcResult result = mockMvc.perform(get(url)).andReturn();
		int status = result.getResponse().getStatus();
		if (status < 200 || status >= 300) {
			throw new IllegalStateException("Unexpected status: " + status + " for url=" + url);
		}
	}


	// --- 파일 출력 유틸 ---

	private void out(String msg) {
		log.info(msg);
		try (PrintWriter pw = new PrintWriter(new FileWriter(RESULT_FILE, true))) {
			pw.println(msg);
			pw.flush();
		} catch (IOException e) {
			// 무시
		}
	}


	// --- 통계 유틸 ---

	private double avg(long[] sorted) {
		long sum = 0;
		for (long v : sorted) sum += v;
		return sum / (double) sorted.length / 1_000_000.0;
	}

	private double percentile(long[] sorted, double p) {
		int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
		return sorted[Math.max(0, Math.min(index, sorted.length - 1))] / 1_000_000.0;
	}


	// --- 데이터 삽입 ---

	// SQL batch INSERT 배치 크기 (한 INSERT 문에 포함되는 VALUES 행 수)
	private static final int SQL_BATCH_SIZE = 2_000;
	// 커밋 간격 (redo log 고갈 방지)
	private static final int COMMIT_INTERVAL = 10_000;

	/**
	 * SQL multi-row INSERT로 대량 데이터 삽입
	 * - products 테이블은 세컨더리 인덱스 없음 (AS-IS) → 인덱스 관리 불필요
	 * - multi-row INSERT: 한 문장에 2000행씩 묶어 네트워크 라운드트립 최소화
	 * - 10,000행마다 COMMIT: MySQL redo log 고갈 방지
	 */
	private void insertBulkData(int productCount, int batchSize) throws Exception {
		try (Connection conn = dataSource.getConnection()) {
			conn.setAutoCommit(false);

			// 1. 브랜드 50개 (소량이므로 단일 multi-row INSERT)
			try (Statement stmt = conn.createStatement()) {
				StringBuilder sb = new StringBuilder();
				sb.append("INSERT INTO brands (name, description, visible_status, created_at, updated_at) VALUES ");
				Timestamp now = Timestamp.from(Instant.now());
				String nowStr = now.toString();
				for (int i = 1; i <= BRAND_COUNT; i++) {
					if (i > 1) sb.append(',');
					sb.append("('Brand_").append(i).append("','Brand description ").append(i)
						.append("','VISIBLE','").append(nowStr).append("','").append(nowStr).append("')");
				}
				stmt.executeUpdate(sb.toString());
				conn.commit();
			}

			// 2. 상품 N건 — multi-row INSERT (products는 세컨더리 인덱스 없음)
			try (Statement stmt = conn.createStatement()) {
				ThreadLocalRandom rng = ThreadLocalRandom.current();
				Instant baseTime = Instant.now();
				int totalInserted = 0;

				for (int offset = 0; offset < productCount; offset += SQL_BATCH_SIZE) {
					int end = Math.min(offset + SQL_BATCH_SIZE, productCount);
					StringBuilder sb = new StringBuilder();
					sb.append("INSERT INTO products (brand_id, name, price, stock, description, created_at, updated_at) VALUES ");

					for (int i = offset; i < end; i++) {
						if (i > offset) sb.append(',');
						long brandId = rng.nextLong(1, BRAND_COUNT + 1);
						long price = rng.nextLong(1_000, 100_001);
						long stock = rng.nextLong(0, 1_001);
						Instant createdAt = baseTime.minus(rng.nextLong(0, 365), ChronoUnit.DAYS);
						String ts = Timestamp.from(createdAt).toString();

						sb.append('(')
							.append(brandId).append(",'Product_").append(i)
							.append("',").append(price)
							.append(',').append(stock)
							.append(",'Description for product ").append(i)
							.append("','").append(ts).append("','").append(ts).append("')");
					}

					stmt.executeUpdate(sb.toString());
					totalInserted += (end - offset);

					// redo log 고갈 방지: COMMIT_INTERVAL마다 커밋
					if (totalInserted % COMMIT_INTERVAL == 0) {
						conn.commit();
					}

					if (totalInserted % 100_000 == 0) {
						log.info("  [INSERT] {}건 완료...", totalInserted);
					}
				}

				// 잔여 트랜잭션 커밋
				conn.commit();
			}
		}
	}


	/**
	 * products + brands 조인 결과를 product_read_model 테이블에 삽입
	 * - DROP INDEX → INSERT → CREATE INDEX (InnoDB 최적 패턴)
	 *   - DISABLE/ENABLE KEYS는 MyISAM 전용이므로 InnoDB에서는 효과 없음
	 *   - 12개 세컨더리 인덱스를 먼저 제거하고, 데이터 삽입 후 일괄 재생성
	 * - TO-BE API는 product_read_model을 조회하므로 데이터 준비 필요
	 */
	private void insertReadModelFromProducts() throws Exception {
		try (Connection conn = dataSource.getConnection();
			 Statement stmt = conn.createStatement()) {

			// 세컨더리 인덱스 제거 (InnoDB: INSERT 중 인덱스 유지 비용 제거)
			dropReadModelIndexes(stmt);

			// 데이터 삽입 (인덱스 없이 순수 INSERT)
			stmt.executeUpdate(
				"INSERT INTO product_read_model (id, brand_id, brand_name, name, price, stock, description, like_count, created_at, updated_at) " +
				"SELECT p.id, p.brand_id, b.name, p.name, p.price, p.stock, p.description, FLOOR(RAND() * 10001), p.created_at, p.updated_at " +
				"FROM products p LEFT JOIN brands b ON b.id = p.brand_id"
			);

			// 인덱스 일괄 재생성 (단일 패스로 B-tree 구축)
			createReadModelIndexes(stmt);
		}
	}


	/**
	 * product_read_model 세컨더리 인덱스 12개 제거
	 * - PK는 유지 (InnoDB clustered index)
	 */
	private void dropReadModelIndexes(Statement stmt) throws SQLException {
		String[] indexes = {
			"idx_read_brand_deleted_created", "idx_read_brand_deleted_price", "idx_read_brand_deleted_likecount",
			"idx_read_deleted_created", "idx_read_deleted_price", "idx_read_deleted_likecount",
			"idx_read_brand_created", "idx_read_brand_price", "idx_read_brand_likecount",
			"idx_read_created", "idx_read_price", "idx_read_likecount"
		};
		for (String idx : indexes) {
			stmt.execute("DROP INDEX " + idx + " ON product_read_model");
		}
	}


	/**
	 * product_read_model 세컨더리 인덱스 12개 재생성
	 * - ProductReadModelEntity @Table(indexes) 정의와 동일
	 */
	private void createReadModelIndexes(Statement stmt) throws SQLException {
		String[] ddls = {
			// 사용자 조회 (브랜드 지정): WHERE brand_id = ? AND deleted_at IS NULL ORDER BY {sort_col}
			"CREATE INDEX idx_read_brand_deleted_created ON product_read_model (brand_id, deleted_at, created_at)",
			"CREATE INDEX idx_read_brand_deleted_price ON product_read_model (brand_id, deleted_at, price)",
			"CREATE INDEX idx_read_brand_deleted_likecount ON product_read_model (brand_id, deleted_at, like_count)",
			// 사용자 조회 (브랜드 미지정): WHERE deleted_at IS NULL ORDER BY {sort_col}
			"CREATE INDEX idx_read_deleted_created ON product_read_model (deleted_at, created_at)",
			"CREATE INDEX idx_read_deleted_price ON product_read_model (deleted_at, price)",
			"CREATE INDEX idx_read_deleted_likecount ON product_read_model (deleted_at, like_count)",
			// 관리자 조회 (브랜드 지정): WHERE brand_id = ? ORDER BY {sort_col}
			"CREATE INDEX idx_read_brand_created ON product_read_model (brand_id, created_at)",
			"CREATE INDEX idx_read_brand_price ON product_read_model (brand_id, price)",
			"CREATE INDEX idx_read_brand_likecount ON product_read_model (brand_id, like_count)",
			// 관리자 조회 (필터 없음): ORDER BY {sort_col}
			"CREATE INDEX idx_read_created ON product_read_model (created_at)",
			"CREATE INDEX idx_read_price ON product_read_model (price)",
			"CREATE INDEX idx_read_likecount ON product_read_model (like_count)"
		};
		for (String ddl : ddls) {
			stmt.execute(ddl);
		}
	}

}
