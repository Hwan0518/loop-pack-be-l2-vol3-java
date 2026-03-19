# Track C — 합류: BC 포트 + 비즈니스 로직 + API + 스케줄러 + E2E

> **선행 조건**: Track A (`02-track-a`) + Track B (`03-track-b`) 완료 후 시작
> **포함 Step**: 5, 6, 12, 13, 14, 15, 16, 16-1, 17, 18, 19, 20
> **실행 방식**: 하나의 터미널에서 직렬 진행

---

## Part 1. BC 포트 (Step 5, 6)

### Step 5. Payment BC → Order BC 포트

**선행**: Track A (Step 4 — Payment Infra) + Track B (Step 2 — OrderStatus)

**신규 파일**:
- `payment/payment/application/port/out/client/order/PaymentOrderReader.java` — interface
- `payment/payment/application/port/out/client/order/PaymentOrderStatusManager.java` — interface
- `payment/payment/infrastructure/acl/order/PaymentOrderReaderImpl.java` — `@Component`, Order BC Facade 호출
- `payment/payment/infrastructure/acl/order/PaymentOrderStatusManagerImpl.java` — `@Component`

**PaymentOrderReader 반환 정보**: userId, orderId, totalPrice, status (DTO 또는 VO로 정의)

**Order BC Facade 확장** (Provider 측):
- `OrderCommandFacade` 또는 별도 API에 `findByIdForUpdate(orderId, userId)` + `changeOrderStatus(orderId, status)` 추가
- FOR UPDATE 조회는 Order BC 내부에서 처리 (비관적 락은 Provider가 제공)

**테스트**: ACL 단위 테스트 (Facade mock)

**근거**: Part 1 §6.1, P1-5 비관적 락

---

### Step 6. Ordering BC → Payment BC 포트 (Step 5와 병렬 가능)

**선행**: Track A (Step 4 — Payment Infra)

**신규 파일**:
- `ordering/order/application/port/out/client/payment/OrderPaymentReader.java` — interface: `existsRequestedPayment(orderId)`
- `ordering/order/infrastructure/acl/payment/OrderPaymentReaderImpl.java` — `@Component`, Payment BC Facade 호출

**Payment BC Facade** (Provider 측):
- `PaymentQueryFacade.existsRequestedByOrderId(orderId)` 추가

**근거**: Part 1 §6.2

---

## Part 2. 비즈니스 로직 (Step 12, 13, 14)

### Step 12. PaymentCommandService

**선행**: Step 4 (Repository), Step 5 (PaymentOrderReader/StatusManager), Step 10 (Resilience4j — 서킷 상태 확인)

**신규 파일**: `payment/payment/application/service/PaymentCommandService.java`

**주요 메서드**:
1. `createPaymentWithLock(userId, orderId, cardType, cardNo, amount)` — Order FOR UPDATE 조회 + 검증 + 멱등성 체크 + 서킷 확인 + Payment 생성 (REQUESTED), @Transactional. Facade의 TX-1 역할
2. `updatePaymentResult(transactionKey, status, reason)` — 콜백/폴링 결과 반영 + Order 상태 동기화 (ACL), @Transactional
3. `failPayment(paymentId, reason)` — PG 요청 실패 시 Payment FAILED + Order PAYMENT_FAILED, @Transactional. Facade의 TX-3 역할
4. `saveTransactionKey(paymentId, transactionKey)` — PG 성공 응답 시 transactionKey 저장, @Transactional. Facade의 TX-2 역할
5. `updatePaymentResultByOrderId(orderId, transactionKey, status, reason)` — transactionKey 없는 Payment의 결과 반영 (폴링 시 orderId로 조회한 경우), @Transactional

**의존**: PaymentCommandRepository, PaymentQueryRepository, PaymentOrderStatusManager

**테스트 (Red 먼저)**:
- 각 메서드의 정상 케이스 + 상태 전이 검증
- `updatePaymentResult()`에서 Payment + Order 동기 업데이트 검증
- 이미 최종 상태(SUCCESS/FAILED)인 Payment에 대한 중복 호출 → skip (멱등)

**근거**: Part 1 P1-2 동기화, §3.1

---

### Step 13. PaymentQueryService (Step 12와 병렬 가능)

**선행**: Step 4 (Repository)

**신규 파일**: `payment/payment/application/service/PaymentQueryService.java`

**주요 메서드**:
1. `findByOrderIdAndStatus(orderId, status)` — 멱등성 체크용
2. `findRequestedPaymentsCreatedBefore(seconds)` — 폴링 대상 조회
3. `existsRequestedByOrderId(orderId)` — 주문 만료 시 진행 중 결제 확인

**근거**: Part 1 P1-5, Part 2 §5.2

---

### Step 14. PaymentCommandFacade — 결제 요청 오케스트레이션

**선행**: Step 11 (PG Gateway), Step 12 (CommandService), Step 13 (QueryService)

**신규 파일**: `payment/payment/application/facade/PaymentCommandFacade.java`

**`requestPayment(userId, PaymentCreateInDto)` 흐름** (§3.1):
```
[TX-1] PaymentCommandService.createPaymentWithLock()  — @Transactional
  ├─ 1. PaymentOrderReader.findOrderForPayment(userId, orderId) — 비관적 락 + 검증
  │     - ORDER_NOT_FOUND / ORDER_NOT_PAYABLE
  ├─ 2. 멱등성 체크 — findByOrderIdAndStatus()
  │     - REQUESTED → PAYMENT_ALREADY_IN_PROGRESS
  │     - FAILED → 새 Payment 생성 허용
  ├─ 3. 서킷 상태 확인 — CircuitBreakerRegistry.getState()
  │     - OPEN → PG_SERVICE_UNAVAILABLE (503)
  ├─ 4. Payment 생성 (REQUESTED)
  │     - 재결제: Order → PENDING_PAYMENT 변경
  └─ 커밋 → 락 해제

PG 호출 — TX 바깥 (Facade에서 직접)
  └─ PgPaymentGateway.requestPayment() [Retry + CB]

[TX-2 or TX-3] 결과 반영
  ├─ 성공 → PaymentCommandService.saveTransactionKey() — @Transactional → 커밋
  └─ 실패 → PaymentCommandService.failPayment() — @Transactional → 커밋
             throw CoreException (TX 이미 커밋, 롤백 대상 없음)
```

**TX 전략 — Facade TX 없음 + Service TX 분리**:

| 항목 | 내용 |
|------|------|
| **결정** | Facade에 `@Transactional` 없음. Service 메서드별 독립 TX |
| **근거 — 롤백 방지** | Facade TX + CoreException(RuntimeException)은 TX 전체 롤백 → Payment FAILED 저장이 사라짐. Service TX 분리로 각 단계 독립 커밋 |
| **근거 — 기존 패턴** | Order Facade도 TX 없이 try-catch로 DataIntegrityViolationException 처리. 동일 패턴 |
| **락 해제 window** | TX-1 커밋 시 비관적 락 해제 → PG 호출 중 ~2초 window. 하지만 TX-1에서 Payment REQUESTED가 이미 커밋되어, 동시 요청은 멱등성 체크에서 거부됨. 만료 스케줄러도 REQUESTED 존재 시 skip. **실질적 위험 없음** |

**테스트 (Red 먼저)**:
- 정상 흐름 (최초 결제)
- 재결제 (PAYMENT_FAILED → 새 Payment)
- 멱등성 거부 (REQUESTED 존재)
- 서킷 OPEN → 503
- PG 실패 → Payment FAILED
- Order 미존재 / 소유권 불일치 → ORDER_NOT_FOUND
- EXPIRED/PAID Order → ORDER_NOT_PAYABLE

**근거**: Part 1 §3.1, P1-5

---

## Part 3. API 레이어 (Step 15, 16)

### Step 15. Payment Controller (Step 16과 병렬)

**선행**: Step 14

**신규 파일**: `payment/payment/interfaces/web/controller/PaymentController.java`

```
@RestController
@RequestMapping("/api/v1/payments")
POST /api/v1/payments
  - @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId
  - @RequestHeader(value = "X-Loopers-LoginPw", required = false) String password
  - @RequestBody @Valid PaymentCreateRequest
  → AuthenticationResolver.resolve(loginId, password) — 헤더 검증 + 사용자 인증 → userId
  → PaymentCommandFacade.requestPayment(userId, inDto)
  → PaymentResponse
```

**인증 패턴**: 기존 프로젝트의 사용자 인증 Controller 패턴과 동일하게 `AuthenticationResolver` 사용.
- `AuthenticationResolver.resolve()` 내부에서 `HeaderValidator.validate()` + `UserQueryFacade.authenticateAndGetUserId()` 호출
- Payment BC Controller에서 `HeaderValidator`를 직접 호출하지 않음 (User BC가 아닌 외부 BC이므로)
- 참고: `OrderCommandController`, `CartItemCommandController` 등 기존 사용자 인증 컨트롤러와 동일 패턴

**테스트**: E2E 테스트 (MockMvc + TestContainers)
- 정상 결제 요청 → 201
- 인증 실패 → 401
- 잘못된 카드 타입/번호 → 400
- EXPIRED 주문 → 400
- 서킷 OPEN → 503

**근거**: Part 1 §5.1

---

### Step 16. Callback Controller (Step 15와 병렬)

**선행**: Step 14

**신규 파일**: `payment/payment/interfaces/web/controller/PaymentCallbackController.java`

```
@RestController
POST /api/v1/payments/callback
  - @RequestBody PaymentCallbackRequest
  - 인증 없음 (PG가 호출)
  → PaymentCommandFacade.handleCallback()
  → 200 OK
```

**Facade handleCallback() 흐름**:
1. transactionKey로 Payment 조회
2. 이미 SUCCESS/FAILED → skip (멱등, 200 반환)
3. Payment + Order 상태 업데이트 (단일 TX, PaymentCommandService.updatePaymentResult())

**테스트**: E2E 테스트
- 정상 콜백 (SUCCESS) → Payment SUCCESS, Order PAID
- 정상 콜백 (FAILED) → Payment FAILED, Order PAYMENT_FAILED
- 중복 콜백 → skip, 200
- 존재하지 않는 transactionKey → PAYMENT_NOT_FOUND

**근거**: Part 1 §4.3, Part 2 §5.1

---

## Part 4. 스케줄러 + 주문 만료 (Step 16-1, 17, 18, 19)

### Step 16-1. 스케줄러 인프라 설정 (독립 — 즉시 가능)

**선행**: 없음 (독립)

**신규 파일**: `payment/payment/support/config/SchedulingConfig.java`

```java
@Configuration
@EnableScheduling
public class SchedulingConfig {}
```

**근거**: 현재 프로젝트에 `@EnableScheduling`이 없음. 폴링 스케줄러(Step 17)와 주문 만료 스케줄러(Step 19)가 `@Scheduled`를 사용하려면 활성화 필수.

---

### Step 17. 결제 상태 폴링 스케줄러 (Step 18과 병렬)

**선행**: Step 11 (PG Gateway), Step 12 (CommandService), Step 13 (QueryService), Step 16-1

**신규 파일**: `payment/payment/application/scheduler/PaymentPollingScheduler.java`

```
@Scheduled(fixedDelay = 10000) — 10초 주기
1. PaymentQueryService.findRequestedPaymentsCreatedBefore(5초) 조회
2. 건별 처리 (개별 try-catch — 한 건 실패가 다른 건에 영향 X):
   a. transactionKey 있음 → PgPaymentGateway.getPaymentByTransactionKey()
   b. transactionKey 없음 (Read timeout) → PgPaymentGateway.getPaymentsByOrderId()
      → PG 응답에서 매칭 트랜잭션 발견 시: Payment에 transactionKey 저장 + 상태 반영
      → 매칭 트랜잭션 없음: Payment → FAILED (요청 미도달로 판단)
   c. PG 결과:
      - SUCCESS/FAILED → PaymentCommandService.updatePaymentResult()
      - PENDING → skip (다음 주기)
      - 트랜잭션 없음 → PaymentCommandService.failPayment()
      - PG 조회 실패 → skip + 에러 로그 (Fallback F6)
```

**테스트**:
- REQUESTED + transactionKey → PG SUCCESS → Payment SUCCESS
- REQUESTED + no transactionKey → PG orderId 조회 → 결과 반영
- PG에 트랜잭션 없음 → Payment FAILED
- PG 조회 실패 → skip, 다음 주기

**근거**: Part 2 §5.2, Part 1 §4.5

---

### Step 18. 수동 복구 API (Step 17과 병렬)

**선행**: Step 11 (PG Gateway), Step 12 (CommandService)

**신규 파일**: Controller + Facade 메서드

```
POST /api/v1/payments/{paymentId}/recover (또는 적절한 엔드포인트)
  → 특정 Payment를 PG에 조회하여 상태 반영
```

**근거**: Part 2 §5.4

---

### Step 19. 주문 만료 스케줄러 + 보상 트랜잭션 (Step 17/18과 병렬 가능)

**선행**: Step 2 (OrderStatus — Order.changeStatus(EXPIRED) 사용), Step 6 (OrderPaymentReader), Step 7 (보상 포트), Step 16-1

**신규 파일**: `ordering/order/application/scheduler/OrderExpirationScheduler.java`

```
@Scheduled(fixedDelay = 60000) — 1분 주기 (만료 시간 N분 대비 충분)
1. OrderQueryService.findExpiredOrders(만료시간) — PENDING_PAYMENT/PAYMENT_FAILED + 만료시간 초과
2. 건별 처리:
   a. OrderPaymentReader.existsRequestedPayment(orderId) → true면 skip
   b. @Transactional 보상 실행:
      - OrderStockManager.restoreStock(각 OrderItem의 productId, quantity)
      - OrderCouponRestorer.restoreCoupon(issuedCouponId) — 쿠폰 사용한 경우만
      - Order.changeStatus(EXPIRED)
      - 로그 기록
```

**Order 조회 메서드 추가**: `OrderQueryRepository.findExpirableOrders(expirationTime)`

**테스트**:
- 만료 대상 주문 → EXPIRED + 재고/쿠폰 복원 확인
- REQUESTED Payment 있는 주문 → skip
- 쿠폰 미사용 주문 → 쿠폰 복원 skip

**근거**: Part 1 P1-7

---

## Part 5. E2E 통합 테스트 (Step 20)

### Step 20. 전체 시나리오 E2E 테스트

**선행**: 전체 Step 완료

**신규 파일**: `payment/payment/interfaces/PaymentControllerE2ETest.java`

**시나리오**:
1. 주문 생성 → 결제 요청 → PG 성공 → 콜백 → Payment SUCCESS, Order PAID
2. 주문 생성 → 결제 → PG 실패 → 재결제 → 성공
3. 결제 진행 중 동일 주문 결제 → PAYMENT_ALREADY_IN_PROGRESS (409)
4. EXPIRED 주문 결제 → ORDER_NOT_PAYABLE (400)
5. 콜백 미수신 → 폴링 스케줄러 → 상태 반영

---

## 체크리스트 충족 매핑 (전체)

### PG 연동 대응

| # | 체크리스트 | 충족 Step | Track |
|---|----------|----------|-------|
| C1 | RestTemplate/FeignClient로 PG 호출 | Step 9 (RestTemplate + 타임아웃) | A |
| C2 | 타임아웃 + 실패 시 예외 처리 | Step 9 (1s/3s) + Step 11 (예외 매핑) | A |
| C3 | 결제 실패 시 시스템 연동 | Step 12 (failPayment) + Step 14 (Facade 흐름) | C |
| C4 | 콜백 + 상태 확인 API 연동 | Step 16 (콜백) + Step 17 (폴링) | C |

### Resilience 설계

| # | 체크리스트 | 충족 Step | Track |
|---|----------|----------|-------|
| R1 | 서킷브레이커/재시도로 장애 확산 방지 | Step 10 (CB + Retry 설정) + Step 11 (적용) | A |
| R2 | 외부 장애 시 내부 정상 응답 | Step 14 (서킷 OPEN → 503), Step 11 (Fallback) | A + C |
| R3 | 콜백 미수신 시 주기/수동 복구 | Step 17 (폴링) + Step 18 (수동 API) | C |
| R4 | 타임아웃 시 결제건 확인 반영 | Step 11 (Read timeout → REQUESTED) + Step 17 (폴링 확인) | A + C |
