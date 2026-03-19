# Round 6 구현 계획

> **구현 순서**: Part 1 (도메인) → Part 2 (Resilience) — 직렬
> **방법론**: TDD (Red → Green → Refactor)
> **기준 문서**: `02-criteria-part1-payment-service.md`, `03-criteria-part2-resilience.md`

### 실행 파일 (implementation-tracks/)

```
01 (ErrorType) 먼저 실행 → 완료 확인
         ↓
터미널 1: 02 (Track A) ████████████─┐
터미널 2: 03 (Track B) ██████──────┘
                                    ↓
               아무 터미널: 04 (Track C) ██████████████████
```

| 파일 | 역할 |
|------|------|
| `implementation-tracks/01-prerequisite-error-types.md` | 선행: ErrorType 추가 |
| `implementation-tracks/02-track-a-payment-domain-and-pg.md` | 터미널 1 — Payment 도메인 + PG 인프라 |
| `implementation-tracks/03-track-b-order-and-compensation.md` | 터미널 2 — Order 변경 + 보상 포트 (병렬) |
| `implementation-tracks/04-track-c-merge.md` | 합류 — BC 포트 → 비즈니스 로직 → API → 스케줄러 → E2E |

---

## Step 의존관계 + 병렬/직렬 구분

### 의존관계 그래프

```
Step 1 (ErrorType)
  ├──→ Step 2 (OrderStatus)  ──────────────────────┐
  └──→ Step 3 (Payment Model) → Step 4 (Payment Infra) ─┤
                                    │                     │
                               ┌────┼────────┐            │
                               ▼    ▼        ▼            ▼
                           Step 13  Step 6   Step 5 ←── Step 2
                           (QuerySvc)(Ord→Pay) (Pay→Ord)

Step 7 (재고/쿠폰 보상) ← Payment 무관, 기존 BC만 수정

Step 8 (DTO) ← Step 3
Step 9 (RestTemplate) ← 독립
Step 10 (Resilience4j) ← 독립

Step 11 (PG Gateway) ← Step 8, 9, 10

Step 12 (CommandService) ← Step 4, 5, 10
Step 14 (Facade) ← Step 11, 12, 13

Step 15 (Controller)  ← Step 14
Step 16 (Callback)    ← Step 14

Step 16-1 (SchedulingConfig) ← 독립 → Step 17, 19 의존
Step 17 (폴링)    ← Step 11, 12, 13
Step 18 (수동복구) ← Step 11, 12

Step 19 (주문만료) ← Step 2, 6, 7, 16-1

Step 20 (E2E) ← 전체
```

### 병렬 가능 구간

| 구간 | 병렬 가능 Step | 선행 조건 |
|------|---------------|----------|
| Phase 1 | **Step 2 ‖ Step 3** | Step 1 완료 후. OrderStatus(Ordering BC)와 Payment 모델(Payment BC)은 서로 무관 |
| Phase 2 | **Step 5 ‖ Step 6 ‖ Step 7** | Step 4 + Step 2 완료 후. 세 포트 모두 서로 다른 BC 방향 |
| Phase 3 | **Step 8 ‖ Step 9 ‖ Step 10** | DTO, RestTemplate 설정, Resilience4j 설정은 완전 독립 |
| Phase 4 | **Step 12 ‖ Step 13** | Step 4 완료 후. CommandService와 QueryService는 독립 (단, Step 12는 추가로 Step 5, 10 필요) |
| Phase 5 | **Step 15 ‖ Step 16** | Step 14 완료 후. 두 Controller는 독립 |
| Phase 6 | **Step 17 ‖ Step 18** | Step 11, 12 완료 후 |
| Cross-Phase | **Step 19는 Phase 4와 병렬 가능** | Step 6, 7만 필요. Payment 비즈니스 로직과 무관 |

### 크리티컬 패스

```
Step 1 → Step 3 → Step 4 → Step 5 → Step 12 → Step 14 → Step 15/16 → Step 20
```

이 경로가 전체 일정을 결정. 나머지 Step은 병렬로 처리하여 단축 가능.

---

## Phase 1. 기반 — ErrorType + 도메인 모델

### Step 1. ErrorType 추가

**대상 파일**: `support/common/error/ErrorType.java`

| ErrorType | HttpStatus | message |
|-----------|-----------|---------|
| `ORDER_NOT_PAYABLE` | 400 | 주문이 결제 가능한 상태가 아닙니다. |
| `PAYMENT_NOT_FOUND` | 404 | 결제 정보를 찾을 수 없습니다. |
| `PAYMENT_ALREADY_IN_PROGRESS` | 409 | 이미 결제가 진행 중입니다. |
| `PG_REQUEST_FAILED` | 502 | PG 결제 요청에 실패했습니다. |
| `PG_SERVICE_UNAVAILABLE` | 503 | PG 서비스를 일시적으로 사용할 수 없습니다. |
| `PG_TIMEOUT` | 504 | PG 응답 시간이 초과되었습니다. |
| `INVALID_CARD_TYPE` | 400 | 지원하지 않는 카드 타입입니다. |
| `INVALID_CARD_NO` | 400 | 잘못된 카드번호 형식입니다. |

**테스트**: `ErrorTypeTest.errorTypeProvider()`에 케이스 추가 + `hasSize(N)` 업데이트

**근거**: Part 1 §7

### Step 2. OrderStatus enum + Order 모델 변경

**신규 파일**:
- `ordering/order/domain/model/enums/OrderStatus.java` — enum + 상태 전이 검증 (`canTransitionTo()`)

**수정 파일**:
- `ordering/order/domain/model/Order.java` — `status` 필드 추가, `changeStatus()` 메서드, `create()`에서 `PENDING_PAYMENT` 기본값
- `ordering/order/infrastructure/entity/OrderEntity.java` — `status` 컬럼 추가 (default `PENDING_PAYMENT`)
- `ordering/order/infrastructure/mapper/OrderEntityMapper.java` — status 매핑

**테스트 (Red 먼저)**:
- `OrderStatusTest` — 허용된 전이 성공, 금지된 전이 예외 (PAID→any, EXPIRED→any 등)
- `OrderTest` — `changeStatus()` 동작, 기본값 PENDING_PAYMENT 확인
- `OrderEntityMapper` 테스트 — status 매핑 확인

**근거**: Part 1 §2.4, §2.5

### Step 3. Payment 도메인 모델

**신규 파일** (`payment/payment/` BC):
- `domain/model/Payment.java` — `create()`, `reconstruct()`, `updateTransactionKey()`, `fail()`, `succeed()`
- `domain/model/enums/PaymentStatus.java` — REQUESTED, SUCCESS, FAILED + 전이 검증
- `domain/model/enums/CardType.java` — SAMSUNG, KB, HYUNDAI

**테스트 (Red 먼저)**:
- `PaymentTest` — create() 검증, 상태 전이(succeed/fail), transactionKey 업데이트
- `PaymentStatusTest` — 허용/금지 전이
- `CardTypeTest` — enum 값 확인

**근거**: Part 1 §2.1, §2.2, §2.3

### Step 4. Payment 인프라 (Entity, Mapper, Repository)

**신규 파일**:
- `infrastructure/entity/PaymentEntity.java` — JPA Entity (`BaseEntity` 상속)
- `infrastructure/mapper/PaymentEntityMapper.java` — `@Component`, `toEntity()` + `toDomain()`
- `domain/repository/PaymentCommandRepository.java` — interface: `save(Payment)`
- `domain/repository/PaymentQueryRepository.java` — interface: `findById()`, `findByOrderIdAndStatus()`, `findByTransactionKey()`, `findRequestedByOrderId()`, `existsRequestedByOrderId()`, `findRequestedPaymentsCreatedBefore(LocalDateTime threshold)`
- `infrastructure/repository/PaymentCommandRepositoryImpl.java`
- `infrastructure/repository/PaymentQueryRepositoryImpl.java`
- `infrastructure/jpa/PaymentJpaRepository.java`

**테스트**:
- `PaymentEntityMapperTest` — toEntity/toDomain 변환
- Repository 통합 테스트 — save, 조회 동작 확인

**근거**: Part 1 §2.1, CLAUDE.md §4.8

---

## Phase 2. BC 간 통신 포트

### Step 5. Payment BC → Order BC 포트

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

### Step 6. Ordering BC → Payment BC 포트

**신규 파일**:
- `ordering/order/application/port/out/client/payment/OrderPaymentReader.java` — interface: `existsRequestedPayment(orderId)`
- `ordering/order/infrastructure/acl/payment/OrderPaymentReaderImpl.java` — `@Component`, Payment BC Facade 호출

**Payment BC Facade** (Provider 측):
- `PaymentQueryFacade.existsRequestedByOrderId(orderId)` 추가

**근거**: Part 1 §6.2

### Step 7. Ordering BC → Catalog/Coupon BC 보상 포트

**수정 파일**:
- `ordering/order/application/port/out/client/catalog/OrderStockManager.java` — `restoreStock(productId, quantity)` 메서드 추가
- Catalog BC: `ProductCommandFacade.increaseStock()` 추가 (Provider)
- `ProductCommandService.increaseStock()` + `Product.increaseStock()` 도메인 메서드

**신규 파일**:
- `ordering/order/application/port/out/client/coupon/OrderCouponRestorer.java` — interface: `restoreCoupon(issuedCouponId)`
- `ordering/order/infrastructure/acl/coupon/OrderCouponRestorerImpl.java` — `@Component`
- Coupon BC: `IssuedCouponCommandFacade.restoreCoupon()` 추가 (Provider)

**Coupon BC Provider 측 도메인/서비스 변경** (필수):
- `IssuedCoupon.java` — `restore()` 도메인 메서드 추가 (status: USED → ISSUED 복원)
- `IssuedCouponCommandService.java` — `restoreCoupon(issuedCouponId)` 서비스 메서드 추가
- `IssuedCouponCommandFacade.java` — `restoreCoupon(issuedCouponId)` Facade 메서드 추가
- **근거**: 현재 IssuedCoupon 모델에는 `use()`만 존재. 쿠폰 복원을 위한 역방향 상태 전이 메서드가 필요.

**테스트**: 각 Port의 단위 테스트 + Provider 측 서비스 테스트

**근거**: Part 1 §6.3, P1-7

---

## Phase 3. PG 연동 인프라 + DTO

### Step 8. DTO 정의

**신규 파일** (`payment/payment/`):

| 유형 | 파일 | 내용 |
|------|------|------|
| Request | `interfaces/web/request/PaymentCreateRequest.java` | orderId, cardType, cardNo + @Valid |
| InDto | `application/dto/in/PaymentCreateInDto.java` | orderId, cardType, cardNo |
| OutDto | `application/dto/out/PaymentOutDto.java` | `from(Payment)` |
| Response | `interfaces/web/response/PaymentResponse.java` | `from(PaymentOutDto)` |

**PG 연동 DTO**:
| 유형 | 파일 | 내용 |
|------|------|------|
| PG Request | `infrastructure/pg/dto/PgPaymentRequest.java` | orderId, cardType, cardNo, amount, callbackUrl |
| PG Response | `infrastructure/pg/dto/PgPaymentResponse.java` | meta + data (transactionKey, status, reason) |
| Callback | `interfaces/web/request/PaymentCallbackRequest.java` | transactionKey, orderId, status, reason 등 7필드 |

**PG 요청 필수 계약**:
- `X-USER-ID` 헤더: PG가 모든 API에 필수로 요구. Payment 생성 시의 userId를 문자열로 전달
- `callbackUrl`: `http://localhost:8080/api/v1/payments/callback` — application.yml의 `payment.pg.callback-url` 설정값으로 관리
- `base-url`: `http://localhost:8082` — application.yml의 `payment.pg.base-url` 설정값으로 관리

**근거**: Part 1 §5, CLAUDE.md §4.9

### Step 9. RestTemplate + 타임아웃 설정

**신규 파일**: `payment/payment/support/config/PgRestTemplateConfig.java`

```
@Configuration → @Bean RestTemplate pgRestTemplate()
- ConnectionTimeout: 1s
- ReadTimeout: 3s
```

**근거**: Part 2 P2-1, P2-2

### Step 10. Resilience4j 설정 (CircuitBreaker + Retry)

**수정 파일**: `build.gradle.kts` — resilience4j-spring-boot3, spring-boot-starter-aop 의존성 추가

**신규 파일 또는 application.yml 설정**:

CircuitBreaker (`pgCircuitBreaker`):
- failure-rate-threshold: 50
- slow-call-duration-threshold: 1s
- slow-call-rate-threshold: 80
- sliding-window-type: COUNT_BASED
- sliding-window-size: 10
- minimum-number-of-calls: 5
- wait-duration-in-open-state: 30s
- permitted-number-of-calls-in-half-open-state: 3

Retry (`pgRetry`):
- max-attempts: 3
- wait-duration: 200ms
- exponential-backoff-multiplier: 2
- enable-randomized-wait: true
- retry-exceptions: PgRequestFailedException, PgConnectionTimeoutException
- ignore-exceptions: PgReadTimeoutException, CallNotPermittedException

**근거**: Part 2 P2-4, P2-6

### Step 11. PG Gateway (PG 호출 + Resilience 적용)

**신규 파일**:
- `payment/payment/application/port/out/client/pg/PgPaymentGateway.java` — interface
- `payment/payment/infrastructure/pg/PgPaymentGatewayImpl.java` — `@Component`

**PgPaymentGatewayImpl 구현**:
- `requestPayment()`: RestTemplate POST → PG 성공 응답 파싱 / 실패 시 예외 변환
- `getPaymentByTransactionKey()`: RestTemplate GET → 트랜잭션 조회
- `getPaymentsByOrderId()`: RestTemplate GET → 주문별 조회

**Resilience 적용**: `Retry(CircuitBreaker(requestPayment()))` 데코레이터 패턴
- Retry scope는 PG HTTP 호출만 감쌈 (Payment 생성은 바깥)

**PG 예외 매핑**:
| PG 상황 | 예외 | Retry |
|---------|------|:-----:|
| HTTP 500 | `PgRequestFailedException` | O |
| Connection timeout | `PgConnectionTimeoutException` | O |
| Read timeout | `PgReadTimeoutException` | X |

**RestTemplate 예외 변환 로직**:
Spring RestTemplate은 Connection timeout과 Read timeout 모두 `ResourceAccessException`으로 감싸므로, PG Gateway에서 cause를 검사하여 커스텀 예외로 변환한다:

```java
try {
    restTemplate.postForEntity(...);
} catch (ResourceAccessException e) {
    if (e.getCause() instanceof ConnectException) {
        throw new PgConnectionTimeoutException(e);  // Retry 대상
    }
    if (e.getCause() instanceof SocketTimeoutException) {
        throw new PgReadTimeoutException(e);         // Retry 제외
    }
    throw e;
}
```

**신규 예외 클래스**:
- `PgConnectionTimeoutException extends RuntimeException` — Connection timeout, Retry 대상
- `PgReadTimeoutException extends RuntimeException` — Read timeout, Retry 제외
- `PgRequestFailedException extends RuntimeException` — PG HTTP 500, Retry 대상

**테스트**: Mock 서버(MockRestServiceServer) 기반 단위 테스트
- PG 성공 응답 파싱
- PG 실패 (HTTP 500) 응답 처리
- 타임아웃 예외 처리
- 서킷 OPEN 시 CallNotPermittedException

**근거**: Part 2 §3, §6.1

---

## Phase 4. 결제 비즈니스 로직

### Step 12. PaymentCommandService

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

### Step 13. PaymentQueryService

**신규 파일**: `payment/payment/application/service/PaymentQueryService.java`

**주요 메서드**:
1. `findByOrderIdAndStatus(orderId, status)` — 멱등성 체크용
2. `findRequestedPaymentsCreatedBefore(seconds)` — 폴링 대상 조회
3. `existsRequestedByOrderId(orderId)` — 주문 만료 시 진행 중 결제 확인

**근거**: Part 1 P1-5, Part 2 §5.2

### Step 14. PaymentCommandFacade — 결제 요청 오케스트레이션

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

**TX 전략 — Facade TX 없음 + Service TX 분리** (방식 A 보완):

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

## Phase 5. API 레이어

### Step 15. Payment Controller (결제 요청)

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

### Step 16. Callback Controller (PG 콜백 수신)

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

## Phase 6. 복구 스케줄러

### Step 16-1. 스케줄러 인프라 설정

**수정 파일**: `payment/payment/support/config/SchedulingConfig.java` (신규)

```java
@Configuration
@EnableScheduling
public class SchedulingConfig {}
```

**근거**: 현재 프로젝트에 `@EnableScheduling`이 없음. 폴링 스케줄러(Step 17)와 주문 만료 스케줄러(Step 19)가 `@Scheduled`를 사용하려면 활성화 필수.

### Step 17. 결제 상태 폴링 스케줄러

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

### Step 18. 수동 복구 API

**신규 파일**: Controller + Facade 메서드

```
POST /api/v1/payments/{paymentId}/recover (또는 적절한 엔드포인트)
  → 특정 Payment를 PG에 조회하여 상태 반영
```

**근거**: Part 2 §5.4

---

## Phase 7. 주문 만료 스케줄러

### Step 19. 주문 만료 스케줄러 + 보상 트랜잭션

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

## Phase 8. E2E 통합 테스트

### Step 20. 전체 시나리오 E2E 테스트

**신규 파일**: `payment/payment/interfaces/PaymentControllerE2ETest.java`

**시나리오**:
1. 주문 생성 → 결제 요청 → PG 성공 → 콜백 → Payment SUCCESS, Order PAID
2. 주문 생성 → 결제 → PG 실패 → 재결제 → 성공
3. 결제 진행 중 동일 주문 결제 → PAYMENT_ALREADY_IN_PROGRESS (409)
4. EXPIRED 주문 결제 → ORDER_NOT_PAYABLE (400)
5. 콜백 미수신 → 폴링 스케줄러 → 상태 반영

---

## 체크리스트 충족 매핑

### PG 연동 대응

| # | 체크리스트 | 충족 Step |
|---|----------|----------|
| C1 | RestTemplate/FeignClient로 PG 호출 | Step 9 (RestTemplate + 타임아웃) |
| C2 | 타임아웃 + 실패 시 예외 처리 | Step 9 (1s/3s) + Step 11 (예외 매핑) |
| C3 | 결제 실패 시 시스템 연동 | Step 12 (failPayment) + Step 14 (Facade 흐름 step 5) |
| C4 | 콜백 + 상태 확인 API 연동 | Step 16 (콜백) + Step 17 (폴링) |

### Resilience 설계

| # | 체크리스트 | 충족 Step |
|---|----------|----------|
| R1 | 서킷브레이커/재시도로 장애 확산 방지 | Step 10 (CB + Retry 설정) + Step 11 (적용) |
| R2 | 외부 장애 시 내부 정상 응답 | Step 14 (서킷 OPEN → 503), Step 11 (Fallback) |
| R3 | 콜백 미수신 시 주기/수동 복구 | Step 17 (폴링) + Step 18 (수동 API) |
| R4 | 타임아웃 시 결제건 확인 반영 | Step 11 (Read timeout → REQUESTED) + Step 17 (폴링 확인) |
