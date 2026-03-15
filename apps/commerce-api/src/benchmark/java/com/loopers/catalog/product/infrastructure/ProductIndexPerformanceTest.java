package com.loopers.catalog.product.infrastructure;


import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * 상품 인덱스 성능 측정 (AS-IS / TO-BE)
 *
 * <p>AS-IS: products 테이블 (PK 외 인덱스 없음) + LEFT JOIN brands + LEFT JOIN likes (LIKES_DESC)
 * <p>TO-BE: product_read_model 테이블 (복합 인덱스) + 단일 테이블 SELECT (JOIN 제거, like_count 비정규화)
 *
 * <p>측정 축: 데이터 규모(10만/100만/1000만) × 트래픽 유형(단일쿼리/버스트/지속부하)
 *
 * <p>1. 단일 쿼리: 6개 유즈케이스 EXPLAIN + 실행시간 (warmup 3회 + 측정 5회 평균)
 * <p>2. 버스트: 100 concurrent 동시 요청 → p50/p95/p99
 * <p>3. 지속 부하: 20 RPS × 10초 → 처리량 + p50/p95/p99
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("상품 인덱스 성능 측정")
class ProductIndexPerformanceTest {

	private static final Logger log = LoggerFactory.getLogger(ProductIndexPerformanceTest.class);
	private static final String RESULT_FILE = "/tmp/index-perf-results.txt";
	private static final int BRAND_COUNT = 50;

	// 버스트 파라미터
	private static final int BURST_THREADS = 100;

	// 지속 부하 파라미터
	private static final int SUSTAINED_RPS = 20;
	private static final int SUSTAINED_DURATION_SEC = 10;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;


	// 로그 + 파일 동시 출력 (Gradle 버퍼링 우회)
	private void out(String msg) {
		log.info(msg);
		try (PrintWriter pw = new PrintWriter(new FileWriter(RESULT_FILE, true))) {
			pw.println(msg);
			pw.flush();
		} catch (IOException e) {
			// 무시
		}
	}


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	// --- 테스트 메서드 (데이터 규모별) ---

	@Test
	@Timeout(value = 5, unit = TimeUnit.MINUTES)
	@DisplayName("[AS-IS] 10만건 성능 측정 (단일쿼리 + 버스트 + 지속부하)")
	void measureAsIs_100K() throws Exception {
		runMeasurement(100_000, "100K", 1_000);
	}


	@Test
	@Timeout(value = 15, unit = TimeUnit.MINUTES)
	@DisplayName("[AS-IS] 100만건 성능 측정 (단일쿼리 + 버스트 + 지속부하)")
	void measureAsIs_1M() throws Exception {
		runMeasurement(1_000_000, "1M", 5_000);
	}


	@Test
	@Timeout(value = 30, unit = TimeUnit.MINUTES)
	@DisplayName("[AS-IS] 1000만건 성능 측정 (단일쿼리 + 버스트 + 지속부하)")
	void measureAsIs_10M() throws Exception {
		runMeasurement(10_000_000, "10M", 10_000);
	}


	// --- TO-BE 테스트 메서드 (Read Model + 복합 인덱스) ---

	@Test
	@Timeout(value = 5, unit = TimeUnit.MINUTES)
	@DisplayName("[TO-BE] 10만건 성능 측정 (Read Model + 복합 인덱스)")
	void measureToBe_100K() throws Exception {
		runToMeasurement(100_000, "100K", 1_000);
	}


	@Test
	@Timeout(value = 15, unit = TimeUnit.MINUTES)
	@DisplayName("[TO-BE] 100만건 성능 측정 (Read Model + 복합 인덱스)")
	void measureToBe_1M() throws Exception {
		runToMeasurement(1_000_000, "1M", 5_000);
	}


	@Test
	@Timeout(value = 30, unit = TimeUnit.MINUTES)
	@DisplayName("[TO-BE] 1000만건 성능 측정 (Read Model + 복합 인덱스)")
	void measureToBe_10M() throws Exception {
		runToMeasurement(10_000_000, "10M", 10_000);
	}


	// --- 핵심 측정 흐름 ---

	private void runMeasurement(int productCount, String label, int batchSize) throws Exception {
		log.info("\n========================================");
		log.info("[{}] AS-IS 성능 측정 시작 (브랜드 {}개, 상품 {}건)", label, BRAND_COUNT, productCount);
		log.info("========================================");

		// 1. 데이터 준비 (brands + products + product_likes)
		long insertStart = System.currentTimeMillis();
		insertBulkData(productCount, batchSize);
		insertBulkLikes(productCount);
		long insertElapsed = System.currentTimeMillis() - insertStart;
		log.info("[{}] 데이터 삽입 완료: {}ms", label, insertElapsed);

		// ANALYZE TABLE로 MySQL 통계 업데이트 (EXPLAIN 정확도 향상)
		try (Connection conn = dataSource.getConnection();
			 Statement stmt = conn.createStatement()) {
			stmt.execute("ANALYZE TABLE products");
			stmt.execute("ANALYZE TABLE brands");
			stmt.execute("ANALYZE TABLE likes");
		}

		// 2. 인덱스 현황
		log.info("\n[{}] ----- 현재 인덱스 -----", label);
		logIndexes("products");

		// 3. 데이터 분포
		log.info("\n[{}] ----- 데이터 분포 -----", label);
		logDataDistribution();

		// 4. 쿼리 정의
		String baseSelect = "SELECT p.id, p.brand_id, b.name AS brand_name, p.name, p.price, p.stock "
			+ "FROM products p LEFT JOIN brands b ON b.id = p.brand_id ";

		// AS-IS LIKES_DESC: Read Model 도입 전 좋아요 순 정렬은 likes 테이블을 JOIN + GROUP BY + COUNT해야 함
		String likesSelect = "SELECT p.id, p.brand_id, b.name AS brand_name, p.name, p.price, p.stock, COUNT(l.id) AS like_count "
			+ "FROM products p LEFT JOIN brands b ON b.id = p.brand_id "
			+ "LEFT JOIN likes l ON l.target_type = 'PRODUCT' AND l.target_id = p.id ";

		String[][] queries = {
			{"UC1: brandId=X, LATEST", baseSelect + "WHERE p.deleted_at IS NULL ORDER BY p.created_at DESC LIMIT 20"},
			{"UC2: brandId=X, PRICE_ASC", baseSelect + "WHERE p.deleted_at IS NULL ORDER BY p.price ASC LIMIT 20"},
			{"UC3: brandId=X, LIKES_DESC", likesSelect + "WHERE p.deleted_at IS NULL GROUP BY p.id ORDER BY like_count DESC LIMIT 20"},
			{"UC4: brandId=1, LATEST", baseSelect + "WHERE p.deleted_at IS NULL AND p.brand_id = 1 ORDER BY p.created_at DESC LIMIT 20"},
			{"UC5: brandId=1, PRICE_ASC", baseSelect + "WHERE p.deleted_at IS NULL AND p.brand_id = 1 ORDER BY p.price ASC LIMIT 20"},
			{"UC6: brandId=1, LIKES_DESC", likesSelect + "WHERE p.deleted_at IS NULL AND p.brand_id = 1 GROUP BY p.id ORDER BY like_count DESC LIMIT 20"},
		};
		String[][] countQueries = {
			{"COUNT: brandId=X", "SELECT COUNT(*) FROM products WHERE deleted_at IS NULL"},
			{"COUNT: brandId=1", "SELECT COUNT(*) FROM products WHERE deleted_at IS NULL AND brand_id = 1"},
		};

		// 5. 단일 쿼리 측정 (EXPLAIN + 실행시간)
		log.info("\n[{}] ===== 단일 쿼리 측정 =====", label);
		for (String[] q : queries) {
			measureSingleQuery(label, q[0], q[1]);
		}
		for (String[] q : countQueries) {
			measureSingleQuery(label, q[0], q[1]);
		}

		// 6. 버스트 측정 (대표 UC: UC1 no-brand latest, UC3 no-brand likes, UC4 brand latest)
		log.info("\n[{}] ===== 버스트 측정 ({}건 동시) =====", label, BURST_THREADS);
		measureBurst(label, queries[0][0], queries[0][1]);
		measureBurst(label, queries[2][0], queries[2][1]);
		measureBurst(label, queries[3][0], queries[3][1]);

		// 7. 지속 부하 측정 (대표 UC)
		log.info("\n[{}] ===== 지속 부하 측정 ({} RPS × {}초) =====", label, SUSTAINED_RPS, SUSTAINED_DURATION_SEC);
		measureSustainedLoad(label, queries[0][0], queries[0][1]);
		measureSustainedLoad(label, queries[2][0], queries[2][1]);
		measureSustainedLoad(label, queries[3][0], queries[3][1]);

		log.info("\n[{}] ===== AS-IS 측정 완료 =====", label);
	}


	/**
	 * TO-BE 핵심 측정 흐름
	 * - product_read_model 테이블 생성 (복합 인덱스 포함)
	 * - brands + products + product_read_model 데이터 삽입
	 * - 단일 테이블 SELECT (JOIN 없음) 6개 유즈케이스 측정
	 */
	private void runToMeasurement(int productCount, String label, int batchSize) throws Exception {
		// 결과 파일 초기화
		try (PrintWriter pw = new PrintWriter(new FileWriter(RESULT_FILE, false))) {
			pw.println("=== TO-BE Index Performance Results ===");
		}

		out("\n========================================");
		out(String.format("[%s] TO-BE 성능 측정 시작 (브랜드 %d개, 상품 %d건, Read Model + 복합 인덱스)", label, BRAND_COUNT, productCount));
		out("========================================");

		// 1. 데이터 준비 (brands + products + product_read_model)
		long insertStart = System.currentTimeMillis();
		insertBulkDataWithReadModel(productCount, batchSize);
		long insertElapsed = System.currentTimeMillis() - insertStart;
		out(String.format("[%s] TO-BE 데이터 삽입 완료: %dms", label, insertElapsed));

		// ANALYZE TABLE로 MySQL 통계 업데이트 (EXPLAIN 정확도 향상)
		try (Connection conn = dataSource.getConnection();
			 Statement stmt = conn.createStatement()) {
			stmt.execute("ANALYZE TABLE products");
			stmt.execute("ANALYZE TABLE brands");
			stmt.execute("ANALYZE TABLE product_read_model");
		}

		// 2. 인덱스 현황
		out(String.format("\n[%s] ----- TO-BE 현재 인덱스 (product_read_model) -----", label));
		logIndexesToFile("product_read_model");

		// 3. 데이터 분포
		out(String.format("\n[%s] ----- TO-BE 데이터 분포 (product_read_model) -----", label));
		logReadModelDataDistributionToFile();

		// 4. 쿼리 정의 (단일 테이블, JOIN 없음)
		String baseSelect = "SELECT id, brand_id, brand_name, name, price, stock, like_count "
			+ "FROM product_read_model ";

		String[][] queries = {
			{"UC1: brandId=X, LATEST", baseSelect + "WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 20"},
			{"UC2: brandId=X, PRICE_ASC", baseSelect + "WHERE deleted_at IS NULL ORDER BY price ASC LIMIT 20"},
			{"UC3: brandId=X, LIKES_DESC", baseSelect + "WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20"},
			{"UC4: brandId=1, LATEST", baseSelect + "WHERE deleted_at IS NULL AND brand_id = 1 ORDER BY created_at DESC LIMIT 20"},
			{"UC5: brandId=1, PRICE_ASC", baseSelect + "WHERE deleted_at IS NULL AND brand_id = 1 ORDER BY price ASC LIMIT 20"},
			{"UC6: brandId=1, LIKES_DESC", baseSelect + "WHERE deleted_at IS NULL AND brand_id = 1 ORDER BY like_count DESC LIMIT 20"},
		};
		String[][] countQueries = {
			{"COUNT: brandId=X", "SELECT COUNT(*) FROM product_read_model WHERE deleted_at IS NULL"},
			{"COUNT: brandId=1", "SELECT COUNT(*) FROM product_read_model WHERE deleted_at IS NULL AND brand_id = 1"},
		};

		// 5. 단일 쿼리 측정 (EXPLAIN + 실행시간)
		out(String.format("\n[%s] ===== TO-BE 단일 쿼리 측정 =====", label));
		for (String[] q : queries) {
			measureSingleQueryToFile(label, q[0], q[1]);
		}
		for (String[] q : countQueries) {
			measureSingleQueryToFile(label, q[0], q[1]);
		}

		// 6. 버스트 측정 (대표 UC: UC1 no-brand latest, UC3 no-brand likes, UC4 brand latest)
		out(String.format("\n[%s] ===== TO-BE 버스트 측정 (%d건 동시) =====", label, BURST_THREADS));
		measureBurstToFile(label, queries[0][0], queries[0][1]);
		measureBurstToFile(label, queries[2][0], queries[2][1]);
		measureBurstToFile(label, queries[3][0], queries[3][1]);

		// 7. 지속 부하 측정 (대표 UC)
		out(String.format("\n[%s] ===== TO-BE 지속 부하 측정 (%d RPS × %d초) =====", label, SUSTAINED_RPS, SUSTAINED_DURATION_SEC));
		measureSustainedLoadToFile(label, queries[0][0], queries[0][1]);
		measureSustainedLoadToFile(label, queries[2][0], queries[2][1]);
		measureSustainedLoadToFile(label, queries[3][0], queries[3][1]);

		out(String.format("\n[%s] ===== TO-BE 측정 완료 =====", label));
	}


	// --- 단일 쿼리 측정 ---

	private void measureSingleQuery(String dataLabel, String ucLabel, String sql) throws Exception {
		try (Connection conn = dataSource.getConnection();
			 Statement stmt = conn.createStatement()) {

			// EXPLAIN
			try (ResultSet rs = stmt.executeQuery("EXPLAIN " + sql)) {
				while (rs.next()) {
					log.info("[{}] [EXPLAIN] {} — table={}, type={}, key={}, rows={}, filtered={}, Extra={}",
						dataLabel, ucLabel,
						rs.getString("table"),
						rs.getString("type"),
						rs.getString("key"),
						rs.getString("rows"),
						rs.getString("filtered"),
						rs.getString("Extra"));
				}
			}

			// Warmup (3회) — 버퍼 풀/JIT/커넥션 풀 안정화
			for (int w = 0; w < 3; w++) {
				try (ResultSet rs = stmt.executeQuery(sql)) {
					while (rs.next()) {
					}
				}
			}

			// 실행시간 측정 (5회)
			int runs = 5;
			long[] times = new long[runs];
			for (int i = 0; i < runs; i++) {
				long start = System.nanoTime();
				try (ResultSet rs = stmt.executeQuery(sql)) {
					while (rs.next()) {
					}
				}
				times[i] = System.nanoTime() - start;
			}

			long sum = 0, min = Long.MAX_VALUE, max = Long.MIN_VALUE;
			for (long t : times) {
				sum += t;
				min = Math.min(min, t);
				max = Math.max(max, t);
			}

			log.info("[{}] [단일쿼리] {} — avg={}ms, min={}ms, max={}ms",
				dataLabel, ucLabel,
				String.format("%.2f", sum / (double) runs / 1_000_000.0),
				String.format("%.2f", min / 1_000_000.0),
				String.format("%.2f", max / 1_000_000.0));
		}
	}


	// --- 버스트 측정 (N개 동시 요청) ---

	private void measureBurst(String dataLabel, String ucLabel, String sql) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(BURST_THREADS);
		CountDownLatch ready = new CountDownLatch(BURST_THREADS);
		CountDownLatch go = new CountDownLatch(1);
		long[] latencies = new long[BURST_THREADS];
		AtomicInteger errors = new AtomicInteger(0);

		// 모든 스레드 준비 후 일제히 시작
		for (int i = 0; i < BURST_THREADS; i++) {
			final int idx = i;
			executor.submit(() -> {
				ready.countDown();
				try {
					go.await();
					long start = System.nanoTime();
					try (Connection conn = dataSource.getConnection();
						 Statement stmt = conn.createStatement();
						 ResultSet rs = stmt.executeQuery(sql)) {
						while (rs.next()) {
						}
					}
					latencies[idx] = System.nanoTime() - start;
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

		// 에러 제외, 정렬
		long[] valid = Arrays.stream(latencies).filter(l -> l > 0).sorted().toArray();
		if (valid.length > 0) {
			log.info("[{}] [버스트] {} — 완료: {}/{}, 에러: {}, avg={}ms, p50={}ms, p95={}ms, p99={}ms, max={}ms",
				dataLabel, ucLabel,
				valid.length, BURST_THREADS, errors.get(),
				String.format("%.2f", avg(valid)),
				String.format("%.2f", percentile(valid, 50)),
				String.format("%.2f", percentile(valid, 95)),
				String.format("%.2f", percentile(valid, 99)),
				String.format("%.2f", valid[valid.length - 1] / 1_000_000.0));
		} else {
			log.warn("[{}] [버스트] {} — 전체 실패 (에러: {})", dataLabel, ucLabel, errors.get());
		}
	}


	// --- 지속 부하 측정 (N RPS × T초) ---

	private void measureSustainedLoad(String dataLabel, String ucLabel, String sql) throws Exception {
		int totalRequests = SUSTAINED_RPS * SUSTAINED_DURATION_SEC;
		long intervalMs = 1000 / SUSTAINED_RPS;

		ExecutorService executor = Executors.newFixedThreadPool(SUSTAINED_RPS * 2);
		List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
		AtomicInteger errors = new AtomicInteger(0);

		long testStart = System.nanoTime();

		// 일정 간격으로 요청 제출
		for (int i = 0; i < totalRequests; i++) {
			executor.submit(() -> {
				long start = System.nanoTime();
				try (Connection conn = dataSource.getConnection();
					 Statement stmt = conn.createStatement();
					 ResultSet rs = stmt.executeQuery(sql)) {
					while (rs.next()) {
					}
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

		// 결과 집계
		long[] sorted = latencies.stream().mapToLong(l -> l).sorted().toArray();
		if (sorted.length > 0) {
			double actualQps = (double) sorted.length / totalTimeMs * 1000;
			log.info("[{}] [지속부하] {} — 완료: {}/{}, 에러: {}, 실제QPS: {}, avg={}ms, p50={}ms, p95={}ms, p99={}ms, 총시간={}ms{}",
				dataLabel, ucLabel,
				sorted.length, totalRequests, errors.get(),
				String.format("%.1f", actualQps),
				String.format("%.2f", avg(sorted)),
				String.format("%.2f", percentile(sorted, 50)),
				String.format("%.2f", percentile(sorted, 95)),
				String.format("%.2f", percentile(sorted, 99)),
				totalTimeMs,
				finished ? "" : " [TIMEOUT — 일부 미완료]");
		} else {
			log.warn("[{}] [지속부하] {} — 전체 실패 (에러: {})", dataLabel, ucLabel, errors.get());
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
	 * AS-IS 좋아요 데이터 삽입 (likes 테이블)
	 * - Read Model 도입 전 좋아요 순 정렬은 likes 테이블을 JOIN + GROUP BY + COUNT해야 함
	 * - 상품당 0~100건 랜덤 좋아요 생성 (user_id는 1~10000 범위)
	 * - multi-row INSERT + COMMIT_INTERVAL 단위 커밋
	 */
	private void insertBulkLikes(int productCount) throws Exception {
		try (Connection conn = dataSource.getConnection()) {
			conn.setAutoCommit(false);

			try (Statement stmt = conn.createStatement()) {
				ThreadLocalRandom rng = ThreadLocalRandom.current();
				Timestamp now = Timestamp.from(Instant.now());
				String nowStr = now.toString();
				int totalInserted = 0;

				for (int productId = 1; productId <= productCount; productId++) {
					// 상품당 0~100건 랜덤 좋아요 (user_id는 1부터 순차 — UNIQUE 제약 회피)
					int likeCount = rng.nextInt(0, 101);
					if (likeCount == 0) {
						continue;
					}

					// multi-row INSERT (SQL_BATCH_SIZE 단위)
					for (int offset = 0; offset < likeCount; offset += SQL_BATCH_SIZE) {
						int end = Math.min(offset + SQL_BATCH_SIZE, likeCount);
						StringBuilder sb = new StringBuilder();
						sb.append("INSERT INTO likes (user_id, target_type, target_id, created_at, updated_at) VALUES ");

						for (int i = offset; i < end; i++) {
							if (i > offset) sb.append(',');
							// user_id = offset + i + 1 (상품별 순차 할당으로 UNIQUE(user_id, target_type, target_id) 보장)
							long userId = i + 1;
							sb.append('(')
								.append(userId).append(",'PRODUCT',")
								.append(productId)
								.append(",'").append(nowStr).append("','").append(nowStr).append("')");
						}

						stmt.executeUpdate(sb.toString());
						totalInserted += (end - offset);

						if (totalInserted % COMMIT_INTERVAL == 0) {
							conn.commit();
						}
					}

					if (productId % 100_000 == 0) {
						log.info("  [INSERT likes] 상품 {}건까지 좋아요 삽입 완료...", productId);
					}
				}

				conn.commit();
				log.info("  [INSERT likes] 총 {}건 좋아요 삽입 완료", totalInserted);
			}
		}
	}


	/**
	 * TO-BE 데이터 삽입: brands + products + product_read_model (복합 인덱스 포함)
	 * - brands, products는 기존 insertBulkData()와 동일하게 삽입
	 * - product_read_model은 DROP INDEX → INSERT → CREATE INDEX (InnoDB 최적 패턴)
	 *   - DISABLE/ENABLE KEYS는 MyISAM 전용이므로 InnoDB에서는 효과 없음
	 *   - 12개 세컨더리 인덱스를 먼저 제거하고, 데이터 삽입 후 일괄 재생성하여 삽입 속도 극대화
	 */
	private void insertBulkDataWithReadModel(int productCount, int batchSize) throws Exception {
		// 1. brands + products 삽입
		insertBulkData(productCount, batchSize);

		// 2. product_read_model 테이블: DROP INDEX → INSERT → CREATE INDEX
		// product_read_model 테이블은 JPA auto-DDL로 이미 생성됨 (ProductReadModelEntity)
		try (Connection conn = dataSource.getConnection();
			 Statement stmt = conn.createStatement()) {
			long start = System.currentTimeMillis();

			// 세컨더리 인덱스 제거 (InnoDB: INSERT 중 인덱스 유지 비용 제거)
			dropReadModelIndexes(stmt);
			long dropElapsed = System.currentTimeMillis() - start;
			out(String.format("  [DROP INDEX] product_read_model 인덱스 제거 완료: %dms", dropElapsed));

			// 데이터 삽입 (인덱스 없이 순수 INSERT)
			long insertStart = System.currentTimeMillis();
			stmt.executeUpdate(
				"INSERT INTO product_read_model (id, brand_id, brand_name, name, price, stock, description, like_count, created_at, updated_at, deleted_at) "
					+ "SELECT p.id, p.brand_id, b.name, p.name, p.price, p.stock, p.description, FLOOR(RAND() * 10001), p.created_at, p.updated_at, p.deleted_at "
					+ "FROM products p LEFT JOIN brands b ON b.id = p.brand_id"
			);
			long insertElapsed = System.currentTimeMillis() - insertStart;
			out(String.format("  [INSERT] product_read_model 데이터 복사 완료: %dms", insertElapsed));

			// 인덱스 일괄 재생성 (단일 패스로 B-tree 구축)
			long createStart = System.currentTimeMillis();
			createReadModelIndexes(stmt);
			long createElapsed = System.currentTimeMillis() - createStart;
			out(String.format("  [CREATE INDEX] product_read_model 인덱스 재생성 완료: %dms", createElapsed));

			long totalElapsed = System.currentTimeMillis() - start;
			out(String.format("  [READ MODEL 총계] DROP+INSERT+CREATE: %dms", totalElapsed));
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


	// --- 데이터 분포 로깅 (Read Model) ---

	private void logReadModelDataDistribution() throws Exception {
		try (Connection conn = dataSource.getConnection();
			 Statement stmt = conn.createStatement()) {

			try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM product_read_model")) {
				rs.next();
				log.info("  전체 상품 (Read Model): {}건", rs.getLong("cnt"));
			}
			try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM product_read_model WHERE deleted_at IS NULL")) {
				rs.next();
				log.info("  활성 상품 (Read Model): {}건", rs.getLong("cnt"));
			}

			try (ResultSet rs = stmt.executeQuery(
				"SELECT MIN(price) AS min_p, MAX(price) AS max_p, AVG(price) AS avg_p FROM product_read_model WHERE deleted_at IS NULL")) {
				rs.next();
				log.info("  가격 (Read Model): min={}, max={}, avg={}",
					rs.getBigDecimal("min_p"), rs.getBigDecimal("max_p"),
					rs.getBigDecimal("avg_p").setScale(0, RoundingMode.HALF_UP));
			}

			try (ResultSet rs = stmt.executeQuery(
				"SELECT MIN(like_count) AS min_l, MAX(like_count) AS max_l, AVG(like_count) AS avg_l FROM product_read_model WHERE deleted_at IS NULL")) {
				rs.next();
				log.info("  좋아요 (Read Model): min={}, max={}, avg={}",
					rs.getLong("min_l"), rs.getLong("max_l"),
					rs.getBigDecimal("avg_l").setScale(0, RoundingMode.HALF_UP));
			}
		}
	}


	// --- 인덱스 로깅 ---

	private void logIndexes(String table) throws Exception {
		try (Connection conn = dataSource.getConnection();
			 Statement stmt = conn.createStatement();
			 ResultSet rs = stmt.executeQuery("SHOW INDEX FROM " + table)) {

			StringBuilder sb = new StringBuilder();
			sb.append(String.format("  %-25s %-20s %-10s%n", "Key_name", "Column_name", "Non_unique"));

			while (rs.next()) {
				sb.append(String.format("  %-25s %-20s %-10d%n",
					rs.getString("Key_name"),
					rs.getString("Column_name"),
					rs.getInt("Non_unique")));
			}

			log.info("[{}]\n{}", table, sb);
		}
	}


	// --- 파일 출력 버전 (TO-BE 전용) ---

	private void logIndexesToFile(String table) throws Exception {
		try (Connection conn = dataSource.getConnection();
			 Statement stmt = conn.createStatement();
			 ResultSet rs = stmt.executeQuery("SHOW INDEX FROM " + table)) {
			StringBuilder sb = new StringBuilder();
			sb.append(String.format("  %-25s %-20s %-10s", "Key_name", "Column_name", "Non_unique"));
			while (rs.next()) {
				sb.append(String.format("\n  %-25s %-20s %-10d",
					rs.getString("Key_name"), rs.getString("Column_name"), rs.getInt("Non_unique")));
			}
			out(sb.toString());
		}
	}

	private void logReadModelDataDistributionToFile() throws Exception {
		try (Connection conn = dataSource.getConnection();
			 Statement stmt = conn.createStatement()) {
			try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM product_read_model")) {
				rs.next();
				out(String.format("  전체 상품 (Read Model): %d건", rs.getLong("cnt")));
			}
			try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM product_read_model WHERE deleted_at IS NULL")) {
				rs.next();
				out(String.format("  활성 상품 (Read Model): %d건", rs.getLong("cnt")));
			}
			try (ResultSet rs = stmt.executeQuery("SELECT MIN(price) AS min_p, MAX(price) AS max_p, AVG(price) AS avg_p FROM product_read_model WHERE deleted_at IS NULL")) {
				rs.next();
				out(String.format("  가격 (Read Model): min=%s, max=%s, avg=%s",
					rs.getBigDecimal("min_p"), rs.getBigDecimal("max_p"),
					rs.getBigDecimal("avg_p").setScale(0, RoundingMode.HALF_UP)));
			}
			try (ResultSet rs = stmt.executeQuery("SELECT MIN(like_count) AS min_l, MAX(like_count) AS max_l, AVG(like_count) AS avg_l FROM product_read_model WHERE deleted_at IS NULL")) {
				rs.next();
				out(String.format("  좋아요 (Read Model): min=%d, max=%d, avg=%s",
					rs.getLong("min_l"), rs.getLong("max_l"),
					rs.getBigDecimal("avg_l").setScale(0, RoundingMode.HALF_UP)));
			}
		}
	}

	private void measureSingleQueryToFile(String dataLabel, String ucLabel, String sql) throws Exception {
		try (Connection conn = dataSource.getConnection();
			 Statement stmt = conn.createStatement()) {
			// EXPLAIN
			try (ResultSet rs = stmt.executeQuery("EXPLAIN " + sql)) {
				while (rs.next()) {
					out(String.format("[%s] [EXPLAIN] %s — table=%s, type=%s, key=%s, rows=%s, filtered=%s, Extra=%s",
						dataLabel, ucLabel, rs.getString("table"), rs.getString("type"),
						rs.getString("key"), rs.getString("rows"), rs.getString("filtered"), rs.getString("Extra")));
				}
			}
			// Warmup
			for (int w = 0; w < 3; w++) {
				try (ResultSet rs = stmt.executeQuery(sql)) { while (rs.next()) {} }
			}
			// 측정
			int runs = 5;
			long[] times = new long[runs];
			for (int i = 0; i < runs; i++) {
				long start = System.nanoTime();
				try (ResultSet rs = stmt.executeQuery(sql)) { while (rs.next()) {} }
				times[i] = System.nanoTime() - start;
			}
			long sum = 0, min = Long.MAX_VALUE, max = Long.MIN_VALUE;
			for (long t : times) { sum += t; min = Math.min(min, t); max = Math.max(max, t); }
			out(String.format("[%s] [단일쿼리] %s — avg=%.2fms, min=%.2fms, max=%.2fms",
				dataLabel, ucLabel, sum / (double) runs / 1_000_000.0,
				min / 1_000_000.0, max / 1_000_000.0));
		}
	}

	private void measureBurstToFile(String dataLabel, String ucLabel, String sql) throws Exception {
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
					long start = System.nanoTime();
					try (Connection conn = dataSource.getConnection();
						 Statement stmt = conn.createStatement();
						 ResultSet rs = stmt.executeQuery(sql)) { while (rs.next()) {} }
					latencies[idx] = System.nanoTime() - start;
				} catch (Exception e) { errors.incrementAndGet(); latencies[idx] = -1; }
			});
		}
		ready.await(); go.countDown();
		executor.shutdown(); executor.awaitTermination(5, TimeUnit.MINUTES);
		long[] valid = Arrays.stream(latencies).filter(l -> l > 0).sorted().toArray();
		if (valid.length > 0) {
			out(String.format("[%s] [버스트] %s — 완료: %d/%d, 에러: %d, avg=%.2fms, p50=%.2fms, p95=%.2fms, p99=%.2fms, max=%.2fms",
				dataLabel, ucLabel, valid.length, BURST_THREADS, errors.get(),
				avg(valid), percentile(valid, 50), percentile(valid, 95),
				percentile(valid, 99), valid[valid.length - 1] / 1_000_000.0));
		} else {
			out(String.format("[%s] [버스트] %s — 전체 실패 (에러: %d)", dataLabel, ucLabel, errors.get()));
		}
	}

	private void measureSustainedLoadToFile(String dataLabel, String ucLabel, String sql) throws Exception {
		int totalRequests = SUSTAINED_RPS * SUSTAINED_DURATION_SEC;
		long intervalMs = 1000 / SUSTAINED_RPS;
		ExecutorService executor = Executors.newFixedThreadPool(SUSTAINED_RPS * 2);
		List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
		AtomicInteger errors = new AtomicInteger(0);
		long testStart = System.nanoTime();
		for (int i = 0; i < totalRequests; i++) {
			executor.submit(() -> {
				long start = System.nanoTime();
				try (Connection conn = dataSource.getConnection();
					 Statement stmt = conn.createStatement();
					 ResultSet rs = stmt.executeQuery(sql)) { while (rs.next()) {} latencies.add(System.nanoTime() - start); }
				catch (Exception e) { errors.incrementAndGet(); }
			});
			long elapsed = (System.nanoTime() - testStart) / 1_000_000;
			long expected = (long) (i + 1) * intervalMs;
			long sleepMs = expected - elapsed;
			if (sleepMs > 0) Thread.sleep(sleepMs);
		}
		executor.shutdown(); boolean finished = executor.awaitTermination(5, TimeUnit.MINUTES);
		long totalTimeMs = (System.nanoTime() - testStart) / 1_000_000;
		long[] sorted = latencies.stream().mapToLong(l -> l).sorted().toArray();
		if (sorted.length > 0) {
			double actualQps = (double) sorted.length / totalTimeMs * 1000;
			out(String.format("[%s] [지속부하] %s — 완료: %d/%d, 에러: %d, 실제QPS: %.1f, avg=%.2fms, p50=%.2fms, p95=%.2fms, p99=%.2fms, 총시간=%dms%s",
				dataLabel, ucLabel, sorted.length, totalRequests, errors.get(), actualQps,
				avg(sorted), percentile(sorted, 50), percentile(sorted, 95),
				percentile(sorted, 99), totalTimeMs, finished ? "" : " [TIMEOUT]"));
		} else {
			out(String.format("[%s] [지속부하] %s — 전체 실패 (에러: %d)", dataLabel, ucLabel, errors.get()));
		}
	}


	// --- 데이터 분포 로깅 ---

	private void logDataDistribution() throws Exception {
		try (Connection conn = dataSource.getConnection();
			 Statement stmt = conn.createStatement()) {

			try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM products")) {
				rs.next();
				log.info("  전체 상품: {}건", rs.getLong("cnt"));
			}
			try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM products WHERE deleted_at IS NULL")) {
				rs.next();
				log.info("  활성 상품: {}건", rs.getLong("cnt"));
			}

			try (ResultSet rs = stmt.executeQuery(
				"SELECT MIN(price) AS min_p, MAX(price) AS max_p, AVG(price) AS avg_p FROM products WHERE deleted_at IS NULL")) {
				rs.next();
				log.info("  가격: min={}, max={}, avg={}",
					rs.getBigDecimal("min_p"), rs.getBigDecimal("max_p"),
					rs.getBigDecimal("avg_p").setScale(0, RoundingMode.HALF_UP));
			}

			try (ResultSet rs = stmt.executeQuery(
				"SELECT MIN(stock) AS min_s, MAX(stock) AS max_s, AVG(stock) AS avg_s FROM products WHERE deleted_at IS NULL")) {
				rs.next();
				log.info("  재고: min={}, max={}, avg={}",
					rs.getLong("min_s"), rs.getLong("max_s"),
					rs.getBigDecimal("avg_s").setScale(0, RoundingMode.HALF_UP));
			}
		}
	}

}
