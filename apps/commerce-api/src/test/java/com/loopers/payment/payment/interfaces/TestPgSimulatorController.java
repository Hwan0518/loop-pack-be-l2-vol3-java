package com.loopers.payment.payment.interfaces;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;


/**
 * 테스트용 PG 시뮬레이터 (E2E 테스트에서 실제 PG 대신 사용)
 * - /test-pg/api/v1/payments 경로로 매핑 (commerce-api 경로와 충돌 방지)
 * - static 메서드로 PG 동작 제어 (테스트별 시나리오 설정)
 */
@RestController
@RequestMapping("/test-pg/api/v1/payments")
public class TestPgSimulatorController {

	// PG 동작 제어
	public enum PgBehavior {
		SUCCESS,          // PG 요청 성공 → PENDING 응답, 이후 SUCCESS 확정
		FAIL_500,         // PG 서버 오류 (HTTP 500)
		READ_TIMEOUT,     // Read timeout (응답 지연)
		RESULT_FAILED     // PG 요청 성공 → PENDING, 이후 FAILED 확정 (한도 초과)
	}

	// 결제 요청(POST) 동작 설정
	private static final AtomicReference<PgBehavior> nextRequestBehavior = new AtomicReference<>(PgBehavior.SUCCESS);
	private static volatile boolean requestStickyMode = false;

	// 조회(GET) 동작 설정
	private static final AtomicReference<PgBehavior> nextQueryBehavior = new AtomicReference<>(PgBehavior.SUCCESS);
	private static volatile boolean queryStickyMode = false;

	// 생성된 트랜잭션 저장소
	private static final ConcurrentHashMap<String, Map<String, Object>> transactions = new ConcurrentHashMap<>();

	// orderId → transactionKey 매핑
	private static final ConcurrentHashMap<String, String> orderTransactionMap = new ConcurrentHashMap<>();


	/**
	 * 테스트 제어 API (static)
	 */

	// 다음 요청의 PG 동작 설정 (1회 후 SUCCESS로 리셋)
	public static void setNextBehavior(PgBehavior behavior) {
		nextRequestBehavior.set(behavior);
		requestStickyMode = false;
	}

	// 고정 모드로 PG 동작 설정 (매 요청마다 동일 동작 — Retry 소진 테스트용)
	public static void setStickyBehavior(PgBehavior behavior) {
		nextRequestBehavior.set(behavior);
		requestStickyMode = true;
	}

	// 다음 조회 요청의 PG 동작 설정 (1회 후 SUCCESS로 리셋)
	public static void setNextQueryBehavior(PgBehavior behavior) {
		nextQueryBehavior.set(behavior);
		queryStickyMode = false;
	}

	// 고정 모드로 조회 요청 동작 설정 (매 요청마다 동일 동작)
	public static void setStickyQueryBehavior(PgBehavior behavior) {
		nextQueryBehavior.set(behavior);
		queryStickyMode = true;
	}

	// 상태 초기화
	public static void reset() {
		nextRequestBehavior.set(PgBehavior.SUCCESS);
		requestStickyMode = false;
		nextQueryBehavior.set(PgBehavior.SUCCESS);
		queryStickyMode = false;
		transactions.clear();
		orderTransactionMap.clear();
	}

	// 특정 트랜잭션의 최종 상태 조회 (테스트 검증용)
	public static String getTransactionStatus(String transactionKey) {
		Map<String, Object> tx = transactions.get(transactionKey);
		return tx != null ? (String) tx.get("status") : null;
	}

	// orderId로 transactionKey 조회
	public static String getTransactionKeyByOrderId(String orderId) {
		return orderTransactionMap.get(orderId);
	}

	// 조회 테스트용 트랜잭션 사전 생성
	public static String seedTransaction(String orderId) {
		String txKey = generateTransactionKey();
		storeTransaction(txKey, orderId, Map.of(
			"cardType", "SAMSUNG",
			"cardNo", "1234-5678-9012-3456",
			"amount", 10000L
		), "SUCCESS", "정상 승인되었습니다.");
		return txKey;
	}


	/**
	 * PG 엔드포인트
	 * 1. 결제 요청
	 * 2. 트랜잭션 키로 조회
	 * 3. 주문 ID로 목록 조회
	 */

	// 1. 결제 요청 — POST /test-pg/api/v1/payments
	@PostMapping
	public ResponseEntity<Map<String, Object>> requestPayment(
		@RequestHeader("X-USER-ID") String userId,
		@RequestBody Map<String, Object> request
	) {

		PgBehavior behavior = consumeRequestBehavior();

		// HTTP 500 시뮬레이션
		ResponseEntity<Map<String, Object>> simulatedError = maybeFailFast(behavior);
		if (simulatedError != null) {
			return simulatedError;
		}

		// Read timeout 시뮬레이션 (5초 대기 — Read timeout 3초 초과)
		if (behavior == PgBehavior.READ_TIMEOUT) {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			// 타임아웃 전에 트랜잭션은 생성됨 (PG에 도달했으므로)
			String txKey = generateTransactionKey();
			String orderId = (String) request.get("orderId");
			String finalStatus = "SUCCESS";
			storeTransaction(txKey, orderId, request, finalStatus, "정상 승인되었습니다.");

			return ResponseEntity.ok(Map.of(
				"meta", Map.of("result", "SUCCESS"),
				"data", Map.of("transactionKey", txKey, "status", "PENDING")
			));
		}

		// 성공 / RESULT_FAILED → PG 요청은 성공, 최종 결과만 다름
		String txKey = generateTransactionKey();
		String orderId = (String) request.get("orderId");

		String finalStatus;
		String reason;
		if (behavior == PgBehavior.RESULT_FAILED) {
			finalStatus = "FAILED";
			reason = "한도초과입니다. 다른 카드를 선택해주세요.";
		} else {
			finalStatus = "SUCCESS";
			reason = "정상 승인되었습니다.";
		}

		storeTransaction(txKey, orderId, request, finalStatus, reason);

		return ResponseEntity.ok(Map.of(
			"meta", Map.of("result", "SUCCESS"),
			"data", Map.of("transactionKey", txKey, "status", "PENDING")
		));
	}


	// 2. 트랜잭션 키로 조회 — GET /test-pg/api/v1/payments/{transactionKey}
	@GetMapping("/{transactionKey}")
	public ResponseEntity<Map<String, Object>> getPayment(
		@RequestHeader("X-USER-ID") String userId,
		@PathVariable String transactionKey
	) {
		PgBehavior behavior = consumeQueryBehavior();

		ResponseEntity<Map<String, Object>> simulatedError = maybeFailFast(behavior);
		if (simulatedError != null) {
			return simulatedError;
		}

		if (behavior == PgBehavior.READ_TIMEOUT) {
			simulateReadTimeout();
		}

		Map<String, Object> tx = transactions.get(transactionKey);
		if (tx == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Map.of("meta", Map.of("result", "FAIL", "errorCode", "NOT_FOUND", "message", "결제 정보를 찾을 수 없습니다.")));
		}

		return ResponseEntity.ok(Map.of(
			"meta", Map.of("result", "SUCCESS"),
			"data", tx
		));
	}


	// 3. 주문 ID로 목록 조회 — GET /test-pg/api/v1/payments?orderId={orderId}
	@GetMapping(params = "orderId")
	public ResponseEntity<Map<String, Object>> getPaymentsByOrderId(
		@RequestHeader("X-USER-ID") String userId,
		@RequestParam String orderId
	) {
		PgBehavior behavior = consumeQueryBehavior();

		ResponseEntity<Map<String, Object>> simulatedError = maybeFailFast(behavior);
		if (simulatedError != null) {
			return simulatedError;
		}

		if (behavior == PgBehavior.READ_TIMEOUT) {
			simulateReadTimeout();
		}

		List<Map<String, Object>> matchingTx = transactions.values().stream()
			.filter(tx -> orderId.equals(tx.get("orderId")))
			.toList();

		return ResponseEntity.ok(Map.of(
			"meta", Map.of("result", "SUCCESS"),
			"data", Map.of(
				"orderId", orderId,
				"transactions", matchingTx
			)
		));
	}


	/**
	 * private method
	 */

	private static PgBehavior consumeRequestBehavior() {
		return requestStickyMode ? nextRequestBehavior.get() : nextRequestBehavior.getAndSet(PgBehavior.SUCCESS);
	}

	private static PgBehavior consumeQueryBehavior() {
		return queryStickyMode ? nextQueryBehavior.get() : nextQueryBehavior.getAndSet(PgBehavior.SUCCESS);
	}

	private static ResponseEntity<Map<String, Object>> maybeFailFast(PgBehavior behavior) {
		if (behavior != PgBehavior.FAIL_500) {
			return null;
		}

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(Map.of(
				"meta", Map.of("result", "FAIL", "errorCode", "Internal Server Error", "message", "일시적인 오류가 발생했습니다."),
				"data", Map.of()
			));
	}

	private static void simulateReadTimeout() {
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	// 트랜잭션 키 생성
	private static String generateTransactionKey() {
		return "TEST:TR:" + UUID.randomUUID().toString().substring(0, 6);
	}

	// 트랜잭션 저장
	private static void storeTransaction(String txKey, String orderId, Map<String, Object> request,
		String status, String reason) {
		Map<String, Object> tx = new ConcurrentHashMap<>();
		tx.put("transactionKey", txKey);
		tx.put("orderId", orderId);
		tx.put("cardType", request.get("cardType"));
		tx.put("cardNo", request.get("cardNo"));
		tx.put("amount", request.get("amount"));
		tx.put("status", status);
		tx.put("reason", reason);

		transactions.put(txKey, tx);
		orderTransactionMap.put(orderId, txKey);
	}

}
