# Round 6 기준점 — Part 2. 서킷브레이커 / 재시도 / Fallback / 타임아웃 설계

> **의사결정 번호**: Part 2는 `P2-1` ~ `P2-8`로 관리
> **Part 1**: `round6-docs/02-criteria-part1-payment-service.md` 참조

---

## Context

PG 시뮬레이터는 **40% 요청 실패 + 100-500ms 지연**이라는 불안정한 특성을 가진다.
이에 대응하여 타임아웃, 재시도, 서킷브레이커, Fallback, 콜백 복구 전략을 설계한다.
commerce-api의 다른 기능(주문 조회, 상품 조회 등)이 PG 장애에 영향받지 않도록 보호하는 것이 핵심이다.

> **PG 동작 특성** (소스 확인 완료):
> - 요청 성공률: 60% (40%는 HTTP 500)
> - 요청 지연: 100ms ~ 500ms (`PaymentApi.kt`)
> - 처리 지연: 1s ~ 5s, `@Async` 비동기 (`PaymentEventListener.kt`)
> - 콜백 재시도: **없음** (`PaymentCoreRelay.kt` — `runCatching` + 에러 로그만)

---

## 1. HTTP Client 설정

### P2-1. HTTP Client 선택

| 항목 | 내용 |
|------|------|
| **결정** | **RestTemplate** 사용 |
| **근거 — 요구사항** | 체크리스트 C1이 "RestTemplate 혹은 FeignClient" 명시 |
| **근거 — 단순성** | PG 연동은 단일 외부 시스템 호출. RestTemplate의 직접 제어가 타임아웃/에러 처리에 명시적 |

### P2-2. 타임아웃 설정값

| 설정 | 값 | 근거 |
|------|-----|------|
| **Connection Timeout** | **1s** | PG가 localhost이므로 연결은 거의 즉시. 1초는 운영 환경 대비 여유 |
| **Read Timeout** | **3s** | PG 최대 지연(500ms)의 6배 여유. 정상 응답을 버리지 않으면서 PG 멈춤 시 빠르게 차단 |

```
commerce-api  ──[TCP 연결]──>  PG(localhost:8082)  ──[응답 대기]──>  응답 수신
               ↑                                     ↑
          Connection Timeout (1s)              Read Timeout (3s)
            "연결이 맺어지는 시간"              "응답이 오는 시간"
```

> **너무 짧으면**: 정상 응답도 타임아웃으로 잘림 (false positive)
> **너무 길면**: 스레드가 오래 블로킹되어 Tomcat 스레드 풀 고갈 (200 max threads 기준)

---

## 2. 서킷브레이커 설정

### P2-3. 서킷브레이커 도입

| 항목 | 내용 |
|------|------|
| **문제** | PG 추가 악화 시 모든 결제 요청이 3초간 블로킹 → Tomcat 스레드 고갈 → **commerce-api 전체 장애** |
| **해결** | 서킷브레이커가 PG 장애 감지 시 **즉시 실패** 반환 → 스레드 보호 → 다른 기능 정상 |
| **체크리스트** | R1 (장애 확산 방지), R2 (외부 장애 시 내부 정상 응답) |

### 서킷브레이커 상태 전이

```
                실패율 < 50%              실패율 >= 50%
    ┌─────────────────────┐         ┌──────────────┐
    │   CLOSED (정상)      │────────>│  OPEN (차단)  │
    │   모든 요청 통과      │         │  즉시 실패    │
    └─────────────────────┘         └──────┬───────┘
              ^                            │
              │     30초 대기 후            │
              │     3건 테스트 요청          v
              │                    ┌──────────────┐
              └────────────────────│  HALF_OPEN   │
                  테스트 성공 시     │  3건만 통과   │
                                   └──────────────┘
```

### P2-4. 서킷브레이커 설정값

| 설정 | 값 | 근거 |
|------|-----|------|
| **Failure Rate Threshold** | **50%** | PG 정상 실패율 40% → 50%에서는 서킷 안 열림. 추가 악화(50%+) 시만 보호. 40% 이하면 정상 운영 중 서킷 반복 개방 |
| **Slow Call Duration** | **1s** | PG 최대 지연 500ms의 2배. 1초 초과 = PG 비정상 |
| **Slow Call Rate Threshold** | **80%** | 80%+ 요청이 느리면 PG 전반적 저하. 간헐적 느린 응답에는 미반응 |
| **Sliding Window Type** | **COUNT_BASED** | 트래픽 적을 때도 일정 샘플 수 보장 |
| **Sliding Window Size** | **10** | 최근 10건 기준. 장애 감지 속도와 오탐 방지의 균형 |
| **Minimum Number of Calls** | **5** | 최소 5건 축적 후 평가. 서버 시작 직후 오판 방지 |
| **Wait Duration in Open** | **30s** | PG 복구 대기. 충분한 시간 부여 + 과도한 대기 방지 |
| **Permitted in Half-Open** | **3** | 3건 테스트로 복구 확신 (1건은 우연 성공 가능) |

> **서킷브레이커가 측정하는 실패**: Retry가 바깥에서 감싸므로(§3), CB의 sliding window에는 **개별 PG 호출의 성공/실패가 모두 기록**된다. 즉, CB는 Retry 결과가 아닌 PG의 실제 건강 상태를 측정한다.

---

## 3. 재시도(Retry) 설정

### P2-5. 재시도 도입

| 항목 | 내용 |
|------|------|
| **문제** | PG 요청 실패율 40%. 단일 시도로는 사용자 체감 성공률이 60%에 불과 |
| **해결** | Resilience4j Retry로 일시적 실패 시 재시도 → 사용자 체감 성공률 93.6%로 향상 |
| **근거 — 요구사항** | 과제 Must-Have에 "Fallback, Timeout, CircuitBreaker" 명시, Nice-To-Have에 "Retryer" 명시. 체크리스트 R1이 "서킷 브레이커 **혹은** 재시도 정책"으로 Retry를 선택지에 포함 |
| **근거 — 실효성** | PG HTTP 500은 일시적 거부(트랜잭션 미생성). 동일 요청을 재시도하면 높은 확률로 성공. 비용 대비 효과가 매우 큼 |
| **근거 — 문서2 권장** | "Circuit Breaker는 Retry와 함께 사용해야 하면 더 강력하게 활용할 수 있습니다" |
| **체크리스트** | R1 (재시도 정책으로 장애 대응) |

### P2-6. 재시도 설정값

| 설정 | 값 | 근거 |
|------|-----|------|
| **Max Attempts** | **3** | 성공률: 1회 60% → 2회 84% → 3회 93.6%. 4회째 개선폭 2.6%p로 미미. 비용 대비 효과의 최적점 |
| **Wait Duration** | **200ms** | PG 지연 100-500ms 감안. 너무 짧으면(50ms) PG 부하 가중, 너무 길면(1s) 사용자 대기 3초+ |
| **Backoff** | **Exponential + Jitter** | 200ms → 400ms (±random). 동시 재시도 요청이 동일 시점에 몰리는 thundering herd 방지 |

#### 재시도 대상/제외 예외

| 예외 | 재시도 여부 | 근거 |
|------|:---------:|------|
| PG HTTP 500 (`meta.result: "FAIL"`) | **O** | PG가 명시적으로 거부 → 트랜잭션 미생성 → 재시도 안전 |
| Connection timeout | **O** | PG에 요청 미도달 확실 → 재시도 안전 |
| Read timeout | **X** | PG에 요청이 **도달했을 가능성** 있음 → 재시도 시 PG에 중복 트랜잭션 생성 위험 |
| `CallNotPermittedException` (서킷 OPEN) | **X** | 서킷이 열려있으면 재시도해도 계속 차단 → 무의미 |
| 비즈니스 예외 (`PAYMENT_ALREADY_IN_PROGRESS` 등) | **X** | 재시도로 해결 불가능한 비즈니스 규칙 위반 |

### Retry + CircuitBreaker 조합

```
[결제 요청]
  │
  └─ Retry ─────────────── wraps ──> CircuitBreaker ──── wraps ──> PG 호출
     (최대 3회, 200ms 간격)            (실패율 50% 이상 시 OPEN)

     ┌─ PG HTTP 500 ─────────────── CB에 실패 기록 → Retry가 재시도 (200ms 대기)
     ├─ Connection timeout ──────── CB에 실패 기록 → Retry가 재시도 (200ms 대기)
     ├─ Read timeout ────────────── CB에 실패 기록 → Retry 제외 → 즉시 throw
     └─ CB OPEN ─────────────────── CallNotPermittedException → Retry 제외 → 즉시 throw → Fallback
```

> **Resilience4j 데코레이터 순서**: `Retry(CircuitBreaker(PG호출))` — Retry가 바깥, CB가 안쪽. CB OPEN 시 Retry도 즉시 중단되므로, 불필요한 재시도가 발생하지 않는다.

> **성공률 개선**: 재시도 없이 60% → 최대 3회 시도 시 **93.6%** (1 - 0.4³)

> **Retry scope**: Retry는 **PG HTTP 호출만** 감싼다. Payment 생성(Step 5)은 Retry 바깥에서 1회만 실행된다. Retry에 Payment 생성이 포함되면 재시도마다 중복 Payment가 생성되므로 반드시 분리해야 한다. (Part 1 §3.1 흐름: Step 5 Payment 생성 → Step 6 Retry(CB(PG 호출)))

> **최악 케이스 응답 시간** (3회 모두 실패 시):
> - **PG HTTP 500**: PG 지연(~500ms) × 3 + 대기(200ms + 400ms) ≈ **2.1초**
> - **Connection timeout**: Connect Timeout(1s) × 3 + 대기(200ms + 400ms) ≈ **3.6초** — 클라우드 환경에서 네트워크 지연이 크면 실제로 발생 가능
>
> Connection timeout 재시도 시 3.6초는 Read Timeout(3s)과 별개의 경로이므로 충돌하지 않지만, 사용자 체감 대기가 길어질 수 있다. 이 trade-off는 재시도 효과(성공률 93.6%)와의 균형에서 수용한다.

---

## 4. Fallback 전략

### P2-7. Fallback 전략

| 항목 | 내용 |
|------|------|
| **결정** | 실패 시나리오별 **단계적 Fallback** 적용 |
| **근거 — 요구사항** | 과제 Must-Have에 "Fallback"이 명시. 단순 에러 반환이 아닌, 각 실패 유형에 맞는 대응 전략 필요 |
| **근거 — UX 보호** | 문서2: "fallbackMethod를 활용해 현재 시스템에서 가능한 대응을 정의해두는 것이 UX와 장애 확산 방지 측면에서 중요" |
| **체크리스트** | R2 (외부 장애 시 내부 정상 응답) |

### 실패 시나리오별 Fallback 매핑

| # | 실패 시나리오 | Fallback 유형 | 사용자 응답 | 시스템 후속 조치 |
|---|-------------|-------------|-----------|---------------|
| F1 | 서킷 OPEN | **Fail-fast** | `PG_SERVICE_UNAVAILABLE` (503) | Payment 미생성, 서킷 복구 대기 |
| F2 | PG HTTP 500 (Retry 소진) | **에러 반환 + 재결제 유도** | `PG_REQUEST_FAILED` (502) | Payment FAILED → Order PAYMENT_FAILED, 재결제 가능 |
| F3 | Connection timeout (Retry 소진) | **에러 반환 + 재결제 유도** | `PG_TIMEOUT` (504) | Payment FAILED → Order PAYMENT_FAILED, 재결제 가능 |
| F4 | Read timeout | **비동기 복구 위임** | `PG_TIMEOUT` (504) | Payment REQUESTED 유지 → 폴링 스케줄러가 PG 확인 (§5.2) |
| F5 | 콜백 미수신 | **폴링 복구** | (비동기, 사용자 무관) | 폴링 스케줄러가 주기적으로 PG 조회 → 상태 반영 (§5.2) |
| F6 | 폴링 중 PG 조회 실패 | **재시도 대기** | (비동기, 사용자 무관) | skip + 에러 로그, 다음 스케줄러 주기에 재확인 |

### F1. 서킷 OPEN Fallback (Fail-fast)

| 항목 | 내용 |
|------|------|
| **동작** | PG 호출 없이 **즉시** `CoreException(ErrorType.PG_SERVICE_UNAVAILABLE)` → **503** 반환 |
| **효과** | 블로킹 0ms → Tomcat 스레드 보호 → commerce-api 다른 기능(주문 조회, 상품 조회 등) 정상 운영 |
| **근거** | 서킷이 열린 상태에서 PG를 호출해봐야 실패 확실. 무의미한 3초 블로킹 대신 즉시 사용자에게 재시도 유도 |

### F2/F3. Retry 소진 Fallback (에러 반환 + 재결제 유도)

| 항목 | 내용 |
|------|------|
| **동작** | 최대 3회 재시도 후에도 실패 → Payment FAILED + Order PAYMENT_FAILED → 에러 응답 (502/504) |
| **효과** | 사용자에게 명확한 실패 사유 전달 + 재결제 경로 유지 (P1-4: 실패 후 재결제 허용) |
| **근거** | PG HTTP 500은 명시적 거부로 트랜잭션 미생성, Connection timeout은 요청 미도달. 두 경우 모두 PG 측 상태 불일치 없이 안전하게 FAILED 처리 가능 |

### F4. Read Timeout Fallback (비동기 복구 위임)

| 항목 | 내용 |
|------|------|
| **동작** | 사용자에게 504 반환하되, Payment는 **REQUESTED 상태 유지** → 폴링 스케줄러가 나중에 PG 결과 확인 |
| **효과** | PG에서 실제로 결제가 성공했을 수 있는 케이스를 안전하게 처리. 사용자 이중 과금 방지 |
| **근거** | 요청이 PG에 도달했을 가능성이 있으므로 FAILED로 마킹하면 "PG에선 결제 성공인데 시스템에선 실패"라는 상태 불일치 발생. REQUESTED 유지 후 폴링으로 확정 |

### F5. 콜백 미수신 Fallback (폴링 복구)

| 항목 | 내용 |
|------|------|
| **동작** | PG 콜백이 오지 않아도, 폴링 스케줄러(§5.2)가 주기적으로 PG에 조회하여 결제 결과를 시스템에 반영 |
| **효과** | PG 콜백 의존도 제거. 콜백 유실/지연과 무관하게 최종적 일관성 보장 |
| **근거** | PG 소스 확인(`PaymentCoreRelay.kt`): 콜백 실패 시 재시도 없음. 콜백만으로는 상태 반영을 보장할 수 없으므로 폴링이 필수 안전망 |

### F6. 폴링 중 PG 조회 실패 Fallback (재시도 대기)

| 항목 | 내용 |
|------|------|
| **동작** | 폴링 스케줄러가 PG에 조회를 시도했으나 PG도 장애 중일 때 → 해당 건 **skip + 에러 로그** 기록, 다음 주기에 재확인 |
| **효과** | 폴링 실패가 다른 REQUESTED 건의 처리를 방해하지 않음 (건별 독립 처리). PG 복구 후 다음 주기에 자동 반영 |
| **근거** | PG 장애는 일시적. 스케줄러가 주기적으로 반복 실행되므로, 한 주기에서 실패해도 다음 주기에 자연스럽게 재시도됨. 별도 복잡한 재시도 로직 불필요 |

---

## 5. 콜백 & 복구 전략

### P2-8. 복구 전략

| 항목 | 내용 |
|------|------|
| **결정** | **스케줄러 자동 폴링 + 수동 복구 API** 둘 다 |
| **근거 — 요구사항** | 체크리스트 R3: "일정 주기 **혹은** 수동 API 호출로 상태를 복구". 둘 다 구현하여 100% 충족 |
| **근거 — PG 콜백 비신뢰** | PG 소스 확인(`PaymentCoreRelay.kt`): 콜백 실패 시 `runCatching` + 에러 로그만 → **재시도 없음** |
| **근거 — 체크리스트 R4** | "타임아웃 실패 시 결제건 확인 반영". Read timeout → REQUESTED 유지 → 폴링이 PG 조회하여 반영 |

### 5.1 콜백 엔드포인트

```
POST /api/v1/payments/callback  (PG가 호출 — 인증 없음, 커스텀 헤더 없음)
```

**처리 규칙:**
- transactionKey로 Payment 조회 → 이미 SUCCESS/FAILED이면 skip (멱등)
- Payment + Order 상태 업데이트 (단일 TX — P1-2 동기화 전략)

**콜백 Body** (PG `TransactionInfo` DTO):
```json
{
  "transactionKey": "20260315:TR:a6e308",
  "orderId": "1351039135",
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451",
  "amount": 5000,
  "status": "SUCCESS",
  "reason": "정상 승인되었습니다."
}
```

### 5.2 결제 상태 폴링 스케줄러 (Payment BC)

| 항목 | 내용 |
|------|------|
| **실행 주기** | **10초** |
| **실행 주기 근거** | PG 처리 지연 최대 5초 + 대상 선정 기준 5초 = 최소 10초 후 첫 폴링 가능. 10초 주기면 REQUESTED Payment가 최대 ~15초 내에 확인됨. 30초 이상은 결제 확정까지 사용자 체감이 느림 |
| **대상** | `REQUESTED` 상태이고 `createdAt` + 5초 < 현재시각인 Payment |
| **5초 기준 근거** | PG 처리 지연이 1-5초이므로, 최소 5초 경과 후 조회해야 PG에서 처리 완료 |
| **transactionKey 있는 경우** | `GET /payments/{transactionKey}` 조회 |
| **transactionKey 없는 경우** (Read timeout) | `GET /payments?orderId={orderId}` 조회 → 매칭 트랜잭션 확인 |
| **PG에 트랜잭션 없음** | Payment → FAILED (요청 미도달로 판단) |
| **PG에서 아직 PENDING** | skip, 다음 주기에 재확인 |
| **PG 결과 확정 (SUCCESS/FAILED)** | Payment/Order 상태 업데이트 (단일 TX) |
| **PG 조회 실패 (PG 장애)** | skip + 에러 로그, 다음 주기에 재확인 (Fallback F6) |

### 5.3 주문 만료 스케줄러 (Ordering BC)

> 상세 흐름은 Part 1의 P1-7 참조

| 항목 | 내용 |
|------|------|
| **대상** | `PENDING_PAYMENT` / `PAYMENT_FAILED` 상태이고 `createdAt` + N분 < 현재시각인 Order |
| **사전 확인** | 해당 주문에 REQUESTED Payment 있으면 skip (결제 진행 중) |
| **동작** | 재고 복원 + 쿠폰 복원 + Order → EXPIRED |

### 5.4 수동 복구 API

특정 결제건을 수동으로 PG에 조회하여 상태 반영하는 API.
스케줄러가 놓친 케이스나 CS 대응에 활용.

---

## 6. PG 에러 매핑

### 6.1 PG 요청 단계 에러

| PG 응답 | commerce-api 처리 | Payment 상태 | 근거 |
|---------|-------------------|-------------|------|
| HTTP 500 + `meta.result: "FAIL"` | **최대 3회 재시도** → 소진 시 `PG_REQUEST_FAILED` (502) | `FAILED` | PG가 요청을 명시적으로 거부. 트랜잭션 미생성 → Retry 대상 (Fallback F2) |
| Connection timeout (1s 초과) | **최대 3회 재시도** → 소진 시 `PG_TIMEOUT` (504) | `FAILED` | PG에 요청 미도달 확실 → Retry 대상 (Fallback F3) |
| Read timeout (3s 초과) | `PG_TIMEOUT` (504), **Retry 제외** | `REQUESTED` | PG에 요청 도달 가능성 있음 → 재시도 시 중복 결제 위험 → 폴링으로 확인 (Fallback F4) |
| 서킷 OPEN | `PG_SERVICE_UNAVAILABLE` (503) | Payment 생성 안 함 | PG 호출 자체를 하지 않음 (Fallback F1) |

> **Read timeout 시 REQUESTED인 이유**: 요청이 PG에 도달했을 가능성이 있음. FAILED로 마킹하면 PG에서 결제 성공인데 시스템에는 실패로 남는 불일치 발생. 폴링 스케줄러가 PG에 확인하여 최종 상태 반영.

### 6.2 콜백 단계 에러 (비동기)

| 콜백 status | Payment 상태 | Order 상태 | reason (PG 소스 확인) |
|------------|-------------|-----------|------|
| `SUCCESS` | → `SUCCESS` | → `PAID` | "정상 승인되었습니다." |
| `FAILED` | → `FAILED` | → `PAYMENT_FAILED` | "한도초과입니다. 다른 카드를 선택해주세요." |
| `FAILED` | → `FAILED` | → `PAYMENT_FAILED` | "잘못된 카드입니다. 다른 카드를 선택해주세요." |

---

## 7. PG 동작 특성 요약 (소스 확인 완료)

| 항목 | 값 | 소스 위치 |
|------|-----|----------|
| 요청 성공률 | 60% (40%는 HTTP 500) | `PaymentApi.kt` — `if ((1..100).random() <= 40)` |
| 요청 지연 | 100ms ~ 500ms | `PaymentApi.kt` — `Thread.sleep((100..500L).random())` |
| 처리 지연 | 1s ~ 5s (`@Async`, `@TransactionalEventListener`) | `PaymentEventListener.kt` — `Thread.sleep((1000L..5000L).random())` |
| 처리 결과 - 성공 | 70% (31-100) | `PaymentApplicationService.kt` |
| 처리 결과 - 한도 초과 | 20% (1-20) | `PaymentApplicationService.kt` — `RATE_LIMIT_EXCEEDED` |
| 처리 결과 - 잘못된 카드 | 10% (21-30) | `PaymentApplicationService.kt` — `RATE_INVALID_CARD` |
| 콜백 재시도 | **없음** | `PaymentCoreRelay.kt` — `runCatching { ... }.onFailure { logger.error }` |
| TransactionKey 형식 | `yyyyMMdd:TR:xxxxxx` | `TransactionKeyGenerator.kt` |
| PG DB 유니크 제약 | (user_id, order_id, transaction_key) | `Payment.kt` Entity |

---
---

# 체크리스트 충족 매핑

## PG 연동 대응

| # | 체크리스트 항목 | 충족 방법 | 관련 섹션 |
|---|---------------|----------|----------|
| C1 | RestTemplate/FeignClient로 PG 호출 | RestTemplate + 타임아웃 | §1 |
| C2 | 타임아웃 + 실패 시 예외 처리 | Connect 1s, Read 3s + PG 에러 매핑 | §1, §6 |
| C3 | 결제 요청 실패 시 시스템 연동 | Retry 소진 후 Payment FAILED + Order PAYMENT_FAILED 저장 | §3, §6, Part1 §7 |
| C4 | 콜백 + 상태 확인 API로 연동 | 콜백 Controller + 폴링 스케줄러 | §5 |

## Resilience 설계

| # | 체크리스트 항목 | 충족 방법 | 관련 섹션 |
|---|---------------|----------|----------|
| R1 | 서킷브레이커 + 재시도로 장애 확산 방지 | Resilience4j CircuitBreaker + Retry 조합 | §2, §3 |
| R2 | 외부 장애 시 내부 정상 응답 | Fallback 전략 (F1~F6): 서킷 OPEN → 503 즉시 반환, Retry 소진 → 에러, Timeout → 비동기 복구 | §4 |
| R3 | 콜백 미수신 시 주기/수동 복구 | 폴링 스케줄러(Fallback F5) + 수동 복구 API | §4, §5 |
| R4 | 타임아웃 실패 시 결제건 확인 반영 | REQUESTED 유지(Fallback F4) → 폴링이 PG 조회 | §4, §6 |

---

## 참고 파일

| 파일 | 용도 |
|------|------|
| PG 소스: `/Users/dhwan/Dev/loopback-be-l2-java-additionals/apps/pg-simulator` | PG 동작 확인용 |
| `apps/commerce-api/src/main/java/com/loopers/support/config/RetryConfig.java` | 기존 @EnableRetry 설정 |
| `apps/commerce-api/src/main/java/com/loopers/cart/cart/application/facade/CartItemCommandFacade.java` | @Retryable 패턴 참고 |
| `build.gradle.kts` | Spring Cloud BOM (resilience4j 추가) |
| `apps/commerce-api/build.gradle.kts` | commerce-api 의존성 추가 |
