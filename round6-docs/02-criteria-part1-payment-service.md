# Round 6 기준점 — Part 1. 결제 서비스 구현

> **의사결정 번호**: Part 1은 `P1-1` ~ `P1-7`로 관리
> **Part 2**: `round6-docs/03-criteria-part2-resilience.md` 참조

---

## Context

주문(Order)에 대한 **결제(Payment) 기능**을 추가한다.
외부 PG 시뮬레이터(`localhost:8082`)와 **비동기 연동**하여 카드 결제를 처리한다.

> **PG 시뮬레이터**: `http://localhost:8082`에서 실행 중
> **PG 소스**: `/Users/dhwan/Dev/loopback-be-l2-java-additionals/apps/pg-simulator`
> **스펙 문서**: `round6-docs/01-pg-simulator-spec.md`

---

## 1. 아키텍처 의사결정

### P1-1. Payment Bounded Context 위치

| 항목 | 내용 |
|------|------|
| **결정** | `payment/payment/` — **별도 Bounded Context**로 분리 |
| **근거 — 업계 표준** | Shopify(2014년 최초 분리), 배달의민족(독립팀 13명), 쿠팡, 토스페이먼츠, 카카오페이 등 모든 대규모 커머스가 결제를 독립 BC/서비스로 운영 |
| **근거 — DDD 원칙** | 주문("Customer", "OrderItem")과 결제("Payer", "Transaction")는 Ubiquitous Language가 다름. Eric Evans도 결제를 Generic Subdomain으로 분류하여 분리 권장 |
| **근거 — 관심사 분리** | 외부 PG 연동(HTTP, 타임아웃, 서킷브레이커, 콜백)은 주문 로직과 무관한 인프라 관심사. 장애 격리 및 독립적 변경 주기 확보 |
| **영향** | Order BC와는 **Port/ACL 패턴**으로 통신. 기존 프로젝트의 Cross-BC 패턴(`OrderStockManager` 등)과 동일 |

### P1-2. Order-Payment 이중 상태 관리 + 동기화 전략

| 항목 | 내용 |
|------|------|
| **결정** | **Order에 status 추가 + Payment도 독립 상태 관리** (둘 다) |
| **근거 — 요구사항** | 체크리스트가 "주문 상태를 안전하게 처리할 방법"을 명시. Order에 status가 없으면 주문의 결제 상태를 매번 Payment 테이블 JOIN으로 확인해야 함 |
| **근거 — 조회 효율** | Order status가 있으면 주문 목록 조회 시 결제 상태를 즉시 확인 가능 (추가 조인 불필요) |
| **근거 — Payment 독립성** | Payment는 PG 연동 상태(REQUESTED → SUCCESS/FAILED)를 추적하는 자체 라이프사이클이 필요. 같은 주문에 대해 여러 결제 시도(실패 → 재결제)가 발생할 수 있으므로 Payment별 상태가 필수 |

#### 동기화 보장 방법

두 BC의 상태가 불일치하면 안 되므로, **단일 트랜잭션 원자성**으로 보장한다:

```
콜백/폴링 수신
  → PaymentCommandService.updatePaymentResult()     // @Transactional
    ├─ 1. Payment status 변경 (save)
    └─ 2. PaymentOrderStatusManager.updateOrderStatus()  // ACL — 같은 TX 참여
          └─ OrderCommandFacade → OrderCommandService    // @Transactional(REQUIRED)
```

| 항목 | 내용 |
|------|------|
| **동기화 메커니즘** | 모놀리스 + 단일 DB → ACL이 같은 JVM 내 메서드 호출 → Spring TX 전파(REQUIRED)로 **같은 트랜잭션에 참여** |
| **원자성** | Payment 저장과 Order 상태 변경이 원자적으로 커밋/롤백 |
| **근거** | 모놀리스에서 이벤트 기반 eventual consistency는 불필요한 복잡성. MSA 전환 시에만 변경 |

#### 상태 주도권 (Source of Truth)

| 상태 변경 시점 | 주도 | 흐름 |
|---------------|------|------|
| 결제 요청 성공 | Payment BC | Payment REQUESTED 생성 → Order는 이미 PENDING_PAYMENT이므로 변경 불필요 |
| PG 요청 실패 | Payment BC | Payment FAILED → Order PAYMENT_FAILED (ACL) |
| 콜백/폴링 결과 | Payment BC | Payment SUCCESS/FAILED → Order PAID/PAYMENT_FAILED (ACL) |
| 주문 만료 | Ordering BC | Order EXPIRED (P1-7). step 2에서 REQUESTED Payment 존재 시 skip하므로 Payment 상태 변경 불필요 |

> **원칙**: Payment BC가 결제 결과의 주도권을 가지며, Order 상태는 항상 **Payment 결과의 파생**이다. 유일한 예외는 주문 만료(P1-7)로, Ordering BC가 주도한다.

### P1-3. 주문-결제 라이프사이클 + 재고/쿠폰 처리

| 항목 | 내용 |
|------|------|
| **결정** | 주문 먼저 생성(PENDING_PAYMENT) → 결제 나중. 재고/쿠폰은 현재 구조 유지 (주문 생성 시 즉시 차감) |
| **근거 — 업계 표준** | 토스페이먼츠("결제 요청 전에 orderId를 서버 DB에 저장해야 한다"), WooCommerce(기본 상태 Pending Payment), Amazon(Pending → 재고 임시 예약) 등 **모든 주요 플랫폼이 주문 먼저 생성** |
| **근거 — PG 요구사항** | PG 결제 요청 시 `orderId` 필수 → 주문이 먼저 존재해야 함 |
| **근거 — 안전성** | "결제 먼저 → 주문 생성" 패턴의 위험: 결제 성공 후 재고 부족(환불), 결제 성공 후 시스템 장애 시 주문 유실 |

#### 재고/쿠폰 현재 구조 유지 근거

| 항목 | 내용 |
|------|------|
| **현재 구조** | 주문 생성 시 재고 즉시 차감 + 쿠폰 즉시 사용 (비가역) |
| **업계와의 차이** | 업계 표준은 "선점 → 확정 → 해제" 2단계 패턴. 현재 프로젝트는 1단계(즉시 확정) |
| **유지 근거** | Round 6 요구사항은 결제 기능 추가에 집중. 선점-확정 패턴 도입은 기존 재고/쿠폰 도메인 전면 수정 필요 (범위 초과) |
| **미결제 리스크 대응** | P1-7 주문 만료 정책으로 해결 — 장기 미결제 주문 만료 시 재고/쿠폰 복원 |

### P1-4. 결제 실패 시 주문 처리

| 항목 | 내용 |
|------|------|
| **결정** | **실패 기록 + 재결제 허용**. Order는 `PAYMENT_FAILED` 상태로 변경되나 재결제 가능 |
| **근거 — 사용자 편의** | 한도 초과 시 다른 카드로 재시도 가능. 주문 재생성 없이 결제만 재시도 |
| **근거 — 비용 절감** | 주문 생성 시 이미 재고 차감, 쿠폰 적용, 장바구니 삭제 완료. 실패마다 롤백하면 보상 트랜잭션 복잡성 증가 |
| **근거 — 이력 보존** | 실패한 결제 기록도 보존하여 분석/CS 대응 가능 |

---

## 2. 도메인 모델 설계

### 2.1 Payment 모델 (신규)

```
Payment
├── id: Long (DB 생성)
├── userId: Long (결제 요청자)
├── orderId: Long (주문 참조)
├── transactionKey: String (PG 발급, nullable — PG 요청 실패/타임아웃 시 없음)
├── cardType: CardType enum (SAMSUNG, KB, HYUNDAI)
├── cardNo: String (카드번호, xxxx-xxxx-xxxx-xxxx)
├── amount: BigDecimal (결제 금액 = Order.totalPrice)
├── status: PaymentStatus enum
├── failureReason: String (nullable — 실패 사유)
└── createdAt: LocalDateTime
```

### 2.2 PaymentStatus enum + 전이 규칙

```
REQUESTED ──→ SUCCESS    (콜백/폴링: PG 결제 성공)
REQUESTED ──→ FAILED     (콜백/폴링: PG 결제 실패 / PG 요청 실패)
SUCCESS   ──→ (변경 불가, 최종 상태)
FAILED    ──→ (변경 불가, 최종 상태)
```

| 상태 | 의미 |
|------|------|
| `REQUESTED` | PG에 결제 요청 완료, 결과 대기 중 |
| `SUCCESS` | 결제 성공 확정 |
| `FAILED` | 결제 실패 확정 |

### 2.3 CardType enum

| 항목 | 내용 |
|------|------|
| **결정** | `SAMSUNG`, `KB`, `HYUNDAI` — Enum으로 정의 |
| **근거** | PG 소스 확인(`PaymentDto.kt`): `enum class CardType { SAMSUNG, KB, HYUNDAI }`. 3종만 허용. Enum으로 입력 검증 + 타입 안전성 확보 |

### 2.4 Order 변경점

| 변경 | 내용 |
|------|------|
| **추가 필드** | `status: OrderStatus` (mutable, non-final) |
| **추가 메서드** | `changeStatus(OrderStatus)` — 상태 전이 검증 포함 |
| **기본값** | `Order.create()`에서 `status = PENDING_PAYMENT` |
| **기존 데이터 호환** | Entity default값으로 `PENDING_PAYMENT` 설정 |

### 2.5 OrderStatus enum + 전이 규칙

```
PENDING_PAYMENT ──→ PAID              (결제 성공)
PENDING_PAYMENT ──→ PAYMENT_FAILED    (결제 실패)
PENDING_PAYMENT ──→ EXPIRED           (미결제 상태로 N분 경과)
PAYMENT_FAILED  ──→ PENDING_PAYMENT   (재결제 시도 시)
PAYMENT_FAILED  ──→ EXPIRED           (재결제 없이 N분 경과)
PAID            ──→ (변경 불가, 최종 상태)
EXPIRED         ──→ (변경 불가, 최종 상태)
```

| 상태 | 의미 |
|------|------|
| `PENDING_PAYMENT` | 결제 대기 (주문 생성 직후 기본값) |
| `PAID` | 결제 완료 |
| `PAYMENT_FAILED` | 결제 실패 (재결제 가능) |
| `EXPIRED` | 주문 만료 (시간 초과, 재고/쿠폰 복원됨, 변경 불가) |

---

## 3. 비즈니스 정책

### P1-5. 멱등성 정책 (같은 주문에 결제 중복 요청 시)

| 현재 상태 | 처리 | 근거 |
|-----------|------|------|
| Payment 없음 | 새 Payment 생성 → PG 요청 | 최초 결제 |
| `REQUESTED` (진행 중) | **거부** (`PAYMENT_ALREADY_IN_PROGRESS`) | 이중 PG 요청 방지. 동시 결제 시 이중 과금 위험 |
| `FAILED` (실패) | **새 Payment 생성** → PG 재요청 | 다른 카드로 재시도 허용 (P1-4에 부합) |

#### 동시성 보장 — 비관적 락

| 항목 | 내용 |
|------|------|
| **문제** | 두 요청이 동시에 멱등성 체크를 통과(둘 다 "Payment 없음")하면 Payment가 2개 생성되어 **이중 과금** 위험 |
| **결정** | §3.1 step 2에서 Order를 **비관적 락(SELECT ... FOR UPDATE)** 으로 조회 → step 3~6까지 락 유지 |
| **효과** | 두 번째 요청은 락 대기 → 첫 번째가 Payment 생성 후 TX 커밋 → REQUESTED 발견 → 거부 |
| **근거 — 기존 패턴** | Product 재고 차감, IssuedCoupon 쿠폰 적용에서 동일한 비관적 락 패턴 사용 중 |
| **근거 — UNIQUE 불가** | `UNIQUE (order_id, status)` → FAILED Payment가 여러 개 가능하므로 제약 불가. MySQL은 partial unique index 미지원 |
| **단점** | PG 호출(최대 ~2초) 동안 Order 락 보유. 같은 주문 동시 결제는 비정상 시나리오이므로 실질적 영향 없음 |

### P1-6. amount 처리

| 항목 | 내용 |
|------|------|
| **결정** | commerce-api 요청에 amount 없음 → `Order.totalPrice`에서 조회하여 PG에 전달 |
| **근거** | 결제 금액은 주문에서 확정된 값. 사용자의 임의 금액 조작을 원천 차단 |

### P1-7. 주문 만료 정책 (미결제 주문 처리)

| 항목 | 내용 |
|------|------|
| **결정** | 주문 생성 후 **N분(예: 30분)** 내 결제 미완료 시 주문 만료 처리 |
| **근거 — 재고 보호** | 미결제 주문이 재고를 영구히 묶는 것 방지. 다른 사용자가 해당 상품을 구매할 수 없는 상태 방지 |
| **근거 — 업계 표준** | 토스페이먼츠(기본 15분, 최대 60분 만료), Amazon(Pending 주문 자동 취소), Adobe Commerce(배치로 만료 주문 스캔) |
| **근거 — 엣지케이스 대응** | 사용자 결제 포기, 앱 강제 종료, 결제 실패 후 재시도 안 함 등 모든 미결제 시나리오를 일괄 처리 |

#### 만료 처리 흐름

```
[스케줄러 — Ordering BC]
  │
  ├─ 1. 만료 대상 조회
  │     조건: status IN (PENDING_PAYMENT, PAYMENT_FAILED)
  │           AND createdAt + 만료시간 < 현재시각
  │
  ├─ 2. 진행 중 결제 확인 (Payment BC 조회, ACL)
  │     └─ 해당 주문에 REQUESTED 상태 Payment 존재? → Skip (결제 진행 중)
  │
  ├─ 3. 보상 트랜잭션 실행 (@Transactional)
  │     ├─ 재고 복원: OrderStockManager.restoreStock(productId, quantity)  [ACL → Catalog BC]
  │     ├─ 쿠폰 복원: OrderCouponRestorer.restoreCoupon(issuedCouponId)   [ACL → Coupon BC]
  │     └─ Order status → EXPIRED
  │
  └─ 4. 로그 기록 (만료된 주문 ID, 복원된 재고/쿠폰 정보)
```

#### 만료 시 보상 대상

| 대상 | 복원 방법 | Port |
|------|----------|------|
| **재고** | product별 quantity 만큼 재고 증가 | `OrderStockManager.restoreStock()` (기존 Port에 메서드 추가) |
| **쿠폰** | issuedCoupon 사용 취소 (couponSnapshot이 있는 경우만) | `OrderCouponRestorer.restoreCoupon()` (신규 Port) |
| **장바구니** | **복원 안 함** | — |

> **장바구니 미복원 근거**: 만료 후 장바구니 복원 시 사용자가 의도하지 않은 항목이 다시 생겨 혼란. 직접 다시 담는 것이 자연스러움.

### 3.1 결제 요청 흐름

```
[사용자] POST /api/v1/payments { orderId, cardType, cardNo }
  │
  ├─ 1. 인증 (X-Loopers-LoginId/Pw → userId 확인)
  ├─ 2. Order 조회 — 비관적 락 (SELECT ... FOR UPDATE, ACL)
  │     ├─ 미존재 / 소유권 불일치 → ORDER_NOT_FOUND 에러 (기존 에러, 보안상 미구분)
  │     ├─ status 검증: PENDING_PAYMENT / PAYMENT_FAILED 만 허용
  │     └─ EXPIRED / PAID → ORDER_NOT_PAYABLE 에러
  ├─ 3. 멱등성 체크 (기존 Payment 상태별 분기 — P1-5)
  ├─ 4. 서킷브레이커 상태 확인
  │     └─ OPEN → Payment 생성 없이 즉시 PG_SERVICE_UNAVAILABLE (503) 반환
  ├─ 5. Payment 생성 (status: REQUESTED)
  │     └─ 재결제인 경우: Order status → PENDING_PAYMENT 변경 (§2.5 상태 전이)
  ├─ 6. PG 결제 요청 — Retry(CircuitBreaker(PG 호출))
  │     ├─ 성공: transactionKey 수신 → Payment에 저장 → 응답
  │     ├─ HTTP 500 / Connection timeout: 최대 3회 재시도 → 소진 시 Payment FAILED → Order PAYMENT_FAILED → 에러
  │     └─ Read timeout: 재시도 없이 즉시 에러 (Payment REQUESTED 유지 → 폴링 복구)
  └─ 7. 응답 반환 (Payment 정보)

[비동기 — 1~5초 후]
PG → POST callbackUrl
  ├─ Payment status 업데이트 (SUCCESS / FAILED)
  └─ Order status 업데이트 (PAID / PAYMENT_FAILED) — 단일 TX 원자성 보장 (P1-2)
```

---

## 4. 엣지케이스 전체 정리

### 4.1 정상 흐름

| # | 시나리오 | 결과 |
|---|---------|------|
| 1 | 주문 → 결제 → PG 성공 → 콜백 SUCCESS | Payment SUCCESS, Order PAID |
| 2 | 주문 → 결제 → PG 실패(HTTP 500) → 재결제 → 성공 | 첫 Payment FAILED, 두 번째 SUCCESS |

### 4.2 PG 응답 관련

| # | 시나리오 | Payment 상태 | Order 상태 | 복구 방법 |
|---|---------|-------------|-----------|----------|
| 3 | PG 요청 실패 (HTTP 500) — Retry 최대 3회 후 소진 | FAILED | PAYMENT_FAILED | 사용자 재결제 |
| 4 | PG 콜백: 한도 초과 | FAILED | PAYMENT_FAILED | 다른 카드로 재결제 |
| 5 | PG 콜백: 잘못된 카드 | FAILED | PAYMENT_FAILED | 다른 카드로 재결제 |
| 6 | Read timeout (3s 초과) — Retry 제외 | REQUESTED | PENDING_PAYMENT | 폴링 스케줄러가 PG 조회 |
| 7 | Connection timeout — Retry 최대 3회 후 소진 | FAILED | PAYMENT_FAILED | 사용자 재결제 |
| 8 | 서킷 OPEN | 생성 안 함 | 변경 없음 | 서킷 복구 후 재시도 |

> **#8 서킷 OPEN 시 Payment 미생성 근거**:
> - Payment는 "PG에 결제를 시도한 기록"이어야 함. PG에 요청을 보내지도 않은 건을 기록하면 데이터 의미가 희석됨
> - 서킷 OPEN 동안 반복 요청 시 의미 없는 FAILED Payment가 대량 생성되는 비효율 방지
> - Order 상태를 건드리지 않으므로 부수효과 없음 (PENDING_PAYMENT 유지 → 서킷 닫히면 바로 결제 가능)
> - 서킷 OPEN 추적은 Resilience4j 메트릭(actuator endpoint, 로그)으로 충분
> - §3.1 흐름의 step 4에서 서킷 상태를 확인하여 OPEN이면 Payment 생성 전에 즉시 503 반환

### 4.3 콜백 관련

| # | 시나리오 | 처리 |
|---|---------|------|
| 9 | 콜백 정상 수신 | Payment/Order 상태 즉시 업데이트 |
| 10 | 콜백 미수신 (PG 콜백 발송 실패) | 폴링 스케줄러가 5초+ 경과 후 PG 조회 → 상태 반영 |
| 11 | 콜백 지연 (5초 이상) | 폴링이 먼저 처리 가능 → 이미 처리된 건은 콜백에서 skip (멱등) |
| 12 | 콜백 중복 수신 | transactionKey로 Payment 조회 → 이미 SUCCESS/FAILED면 skip (멱등) |

> **PG 소스 확인** (`PaymentCoreRelay.kt`): PG는 콜백 실패 시 `runCatching` + 에러 로그만 남기고 **재시도하지 않음**. 폴링 복구 필수.

### 4.4 사용자 행동 관련

| # | 시나리오 | 처리 |
|---|---------|------|
| 13 | 결제 포기 (주문 생성 후 결제 안 함) | 만료 스케줄러: N분 후 Order EXPIRED, 재고/쿠폰 복원 (P1-7) |
| 14 | 결제 실패 후 재시도 안 함 | 만료 스케줄러: N분 후 Order EXPIRED, 재고/쿠폰 복원 (P1-7) |
| 15 | 결제 중 앱 강제 종료 | Payment REQUESTED 유지 → 콜백/폴링으로 결과 반영. 이후 미결제 시 만료 |
| 16 | 같은 주문에 동시 결제 2건 | Order 비관적 락으로 순차 처리. 첫 요청: Payment 생성. 두 번째: REQUESTED 발견 → PAYMENT_ALREADY_IN_PROGRESS 거부 (P1-5) |
| 17 | EXPIRED 주문에 결제 시도 | ORDER_NOT_PAYABLE 에러 |

### 4.5 폴링 스케줄러 관련

| # | 시나리오 | 처리 |
|---|---------|------|
| 18 | REQUESTED + transactionKey 있음 | PG `GET /payments/{transactionKey}` 조회 → 결과 반영 |
| 19 | REQUESTED + transactionKey 없음 (Read timeout) | PG `GET /payments?orderId={orderId}` 조회 → 매칭 확인 |
| 20 | PG에 해당 트랜잭션 없음 (요청 미도달) | Payment → FAILED 처리 |
| 21 | PG에서 아직 PENDING | skip, 다음 주기에 재확인 |

---

## 5. API 스펙

### 5.1 Commerce API — 결제 요청

```http
POST /api/v1/payments
X-Loopers-LoginId: {loginId}
X-Loopers-LoginPw: {password}
Content-Type: application/json

{
  "orderId": "1351039135",
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451"
}
```

### 5.2 PG 시뮬레이터 API (소스 확인 완료)

#### 결제 요청 — `POST /api/v1/payments`

**Request** (`X-USER-ID` 헤더 필수):
```json
{
  "orderId": "1351039135",
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451",
  "amount": 5000,
  "callbackUrl": "http://localhost:8080/api/v1/payments/callback"
}
```

**성공 응답 (HTTP 200, 60%)**:
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { "transactionKey": "20260315:TR:a6e308", "status": "PENDING", "reason": null }
}
```

**실패 응답 (HTTP 500, 40%)**:
```json
{
  "meta": { "result": "FAIL", "errorCode": "Internal Server Error", "message": "일시적인 오류가 발생했습니다." },
  "data": null
}
```

#### 트랜잭션 조회 — `GET /api/v1/payments/{transactionKey}`

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "transactionKey": "20260315:TR:a6e308", "orderId": "1351039135",
    "cardType": "SAMSUNG", "cardNo": "1234-5678-9814-1451",
    "amount": 5000, "status": "SUCCESS", "reason": "정상 승인되었습니다."
  }
}
```

#### 주문별 조회 — `GET /api/v1/payments?orderId={orderId}`

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "orderId": "1351039135",
    "transactions": [
      { "transactionKey": "...", "status": "SUCCESS", "reason": "정상 승인되었습니다." }
    ]
  }
}
```

### 5.3 콜백 (PG → Commerce) — 소스 확인 완료

| 항목 | 내용 |
|------|------|
| **URL** | `POST {callbackUrl}` (결제 요청 시 지정) |
| **Content-Type** | `application/json` |
| **커스텀 헤더** | 없음 |
| **재시도** | **없음** — 실패 시 에러 로그만 (`PaymentCoreRelay.kt`) |

**콜백 status별 reason** (PG 소스 확인):

| status | reason |
|--------|--------|
| `SUCCESS` (70%) | `"정상 승인되었습니다."` |
| `FAILED` (20%) | `"한도초과입니다. 다른 카드를 선택해주세요."` |
| `FAILED` (10%) | `"잘못된 카드입니다. 다른 카드를 선택해주세요."` |

### 5.4 PG Validation 규칙 (소스 확인)

| 검증 대상 | 규칙 | 실패 시 |
|-----------|------|---------|
| `X-USER-ID` 헤더 | 필수 | BAD_REQUEST: "유저 ID 헤더는 필수입니다." |
| `orderId` | 6자리 이상 | BAD_REQUEST: "주문 ID는 6자리 이상 문자열이어야 합니다." |
| `cardNo` | `xxxx-xxxx-xxxx-xxxx` | BAD_REQUEST: "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다." |
| `amount` | 양의 정수 | BAD_REQUEST: "결제금액은 양의 정수여야 합니다." |
| `callbackUrl` | `http://localhost:8080` 시작 | BAD_REQUEST: "콜백 URL 은 http://localhost:8080 로 시작해야 합니다." |

---

## 6. BC 간 통신

### 6.1 Payment BC → Order BC

| Port 인터페이스 (Payment BC) | 역할 | ACL 구현체 |
|------------------------------|------|-----------|
| `PaymentOrderReader` | Order 조회 (userId, orderId → 정보 + totalPrice + status) | `PaymentOrderReaderImpl` → Order BC Facade |
| `PaymentOrderStatusManager` | Order 상태 변경 (orderId, OrderStatus) | `PaymentOrderStatusManagerImpl` → Order BC Facade |

### 6.2 Ordering BC → Payment BC (주문 만료 시)

| Port 인터페이스 (Ordering BC) | 역할 | ACL 구현체 |
|-------------------------------|------|-----------|
| `OrderPaymentReader` | 주문의 REQUESTED Payment 존재 여부 확인 | `OrderPaymentReaderImpl` → Payment BC Facade |

### 6.3 Ordering BC → Catalog/Coupon BC (만료 시 보상)

| Port 인터페이스 (Ordering BC) | 역할 | 비고 |
|-------------------------------|------|------|
| `OrderStockManager.restoreStock()` | 재고 복원 | 기존 Port에 메서드 추가 |
| `OrderCouponRestorer.restoreCoupon()` | 쿠폰 복원 | 신규 Port |

---

## 7. 에러 처리

| ErrorType | HttpStatus | 용도 |
|-----------|-----------|------|
| `ORDER_NOT_PAYABLE` | 400 | 주문이 결제 가능 상태가 아님 (PAID/EXPIRED 등) |
| `PAYMENT_NOT_FOUND` | 404 | 결제 정보 없음 |
| `PAYMENT_ALREADY_IN_PROGRESS` | 409 | 이미 결제 진행 중 (P1-5) |
| `PG_REQUEST_FAILED` | 502 | PG 결제 요청 실패 (HTTP 500 수신) |
| `PG_SERVICE_UNAVAILABLE` | 503 | 서킷브레이커 OPEN / PG 연결 불가 |
| `PG_TIMEOUT` | 504 | PG 응답 타임아웃 |
| `INVALID_CARD_TYPE` | 400 | 미지원 카드 타입 |
| `INVALID_CARD_NO` | 400 | 잘못된 카드번호 형식 |

---

## 8. 기존 코드 준수사항 (변경 없음)

| 항목 | 기존 패턴 |
|------|----------|
| 레이어 구조 | Controller → Facade → Service → Repository |
| TX 경계 | Facade/Service 모두 메서드 레벨 `@Transactional` 사용 가능 — 비즈니스/기술적 필요에 따라 판단. 클래스 레벨 사용 금지 |
| 도메인 모델 | `create()` + `reconstruct()`, private 생성자 |
| DTO 흐름 | Request → InDto → Domain → OutDto → Response |
| CQRS | Command/Query 분리 |
| Entity 매핑 | `{Domain}EntityMapper` (@Component) |
| 에러 처리 | `CoreException` + `ErrorType` |
| 테스트 | TDD (Red-Green-Refactor), 3A, @DisplayName 상세 |
| 주석 | Comment-First, 한국어 비즈니스/영어 마커 |
| 인증 | `X-Loopers-LoginId/Pw` → HeaderValidator |

---

## 참고 파일

| 파일 | 용도 |
|------|------|
| `round6-docs/00-requirements.md` | 요구사항 원문 |
| `round6-docs/01-pg-simulator-spec.md` | PG 시뮬레이터 API 스펙 |
| PG 소스: `/Users/dhwan/Dev/loopback-be-l2-java-additionals/apps/pg-simulator` | PG 동작 확인용 |
| `apps/commerce-api/src/main/java/com/loopers/ordering/order/domain/model/Order.java` | Order 모델 (status 추가) |
| `apps/commerce-api/src/main/java/com/loopers/ordering/order/application/facade/OrderCommandFacade.java` | 기존 Facade 패턴 |
| `apps/commerce-api/src/main/java/com/loopers/ordering/order/infrastructure/entity/OrderEntity.java` | OrderEntity (status 추가) |
| `apps/commerce-api/src/main/java/com/loopers/ordering/order/application/port/out/client/catalog/OrderStockManager.java` | 재고 Port (restoreStock 추가) |
| `apps/commerce-api/src/main/java/com/loopers/support/common/error/ErrorType.java` | ErrorType 추가 |
