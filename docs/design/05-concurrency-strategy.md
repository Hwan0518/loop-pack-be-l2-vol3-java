# 05. 동시성 제어 전략

> **문서 상태**
> - 성격: 현재 전략과 후속 TODO가 함께 있는 문서
> - 현재 구현과 일치하는 항목: 좋아요 수 원자적 카운터, 재고 차감 비관적 락, 쿠폰 사용 비관적 락
> - TODO/미구현 항목: 주문 후 장바구니 정리 Event 전환, 상품/브랜드 삭제 시 좋아요 정리 제거, 상품 삭제 후 장바구니 정리 방식 재정의
> - 현재 구현 판단은 실제 코드 우선이며, 본문 3장 이후의 개선 방향은 즉시 코드 상태를 뜻하지 않는다

## 1. 선택 기준

### 1-1. UPDATE — 의사결정 트리

```
Q0. 애초에 동시성 경합이 현실적으로 발생하는가?
  ├── No → 락 불필요 (과잉 방어 제거)
  └── Yes
        │
        Q1. 단순 증감(+1/-1)이고, 복잡한 비즈니스 검증이 없는가?
          ├── Yes → 원자적 카운터
          └── No (복잡한 비즈니스 검증 필요)
                │
                Q2. 빠른 응답 기대 + 경합 낮음?
                  ├── Yes → 낙관적 락
                  └── No (정확성 중요 + 경합 높음)
                        └── 비관적 락
```

| 방식 | 맞는 상황 | 핵심 포인트 |
|------|----------|-------------|
| 원자적 카운터 | 단순 증감, 검증 없음 | DB가 단일 SQL로 처리 |
| 낙관적 락 | 경합 낮음, 상태 변경 로직 복잡함 | 충돌 시 재시도 |
| 비관적 락 | 경합 높음, 정확성 절대적 | 읽는 시점부터 잠금 |

### 1-2. INSERT — 의사결정 트리

```
Q1. 비즈니스 규칙상 중복이 허용되지 않는가?
  ├── Yes
  │     ├── DB UNIQUE constraint (데이터 무결성 안전망 — 항상 적용)
  │     └── 사전 조회 select-before-insert (비즈니스 검증 — 의미 있는 에러 메시지 제공)
  │           ※ TOCTOU gap이 존재하므로 동시성 방어는 불가, 비즈니스 검증 목적만 담당
  │
  └── Q2. 사전 조회를 통과한 0.01% race에서 unique 위반 발생 시, 어떻게 처리하는가?
        ├── 부수 효과 없거나 미미 → 500 허용 (재시도 시 사전 조회에서 정상 응답)
        ├── 부수 효과 없으나 수량 합산 누락 위험 → @Retryable (재시도로 자동 해소)
        └── 부수 효과가 크고 비가역적 → Facade에서 try-catch + 멱등 반환
```

#### INSERT 동시성 처리 원칙

| 원칙 | 설명 |
|------|------|
| DB UNIQUE constraint | 데이터 무결성 안전망. 동시성 제어 수단이 아닌 **무결성 보장** 수단으로 항상 적용 |
| 사전 조회 (select-before-insert) | 비즈니스 검증 목적. 정상 흐름에서 의미 있는 에러/멱등 반환 제공 |
| RepositoryImpl try-catch 금지 | Repository는 데이터를 반환하는 역할만 담당. 비즈니스 예외 발생은 Service 책임 |
| race 시 500 허용 기준 | 부수 효과가 없거나 미미한 경우. 재시도 시 사전 조회에서 올바른 응답 보장 |
| race 시 try-catch 유지 기준 | 부수 효과가 크고 비가역적인 경우 (재고 차감, 장바구니 삭제 등이 이미 커밋된 상태에서 500 반환 시 사용자가 결과를 인지 못하는 비용이 큰 경우) |

---

## 2. 유스케이스별 적용 결과

| 유스케이스 | 유형 | 전략 | 근거 |
|-----------|------|------|------|
| 좋아요 수 증감 | UPDATE | 원자적 카운터 | 단순 `+1/-1`, 검증 없음, 높은 경합, 즉시 응답 기대 |
| 재고 차감 | UPDATE | 비관적 락 | Stock VO 검증 필요, 높은 경합, 정확성 절대적 |
| 쿠폰 사용 | UPDATE | 비관적 락 | 1쿠폰 = 1사용자로 경합 가능성은 낮으나, 주문 TX 내에서 쿠폰 상태 변경의 원자성을 보장하기 위해 `PESSIMISTIC_WRITE` 유지 |
| 쿠폰 발급 | INSERT | UNIQUE constraint | `(user_id, coupon_template_id)` 복합 유니크. race 시 500 허용 |
| 주문 생성 | INSERT | Idempotency key + try-catch | `(user_id, request_id)` 유니크. race 시 Facade catch → 멱등 반환 (부수 효과 비가역적) |
| 좋아요 행 | INSERT | UNIQUE constraint | `(user_id, target_type, target_id)` 복합 유니크. race 시 500 허용 |
| 장바구니 항목 | INSERT/UPDATE | UNIQUE constraint + 사전 조회 + `@Retryable` | `(user_id, product_id)` 복합 유니크. 사전 조회 → 존재 시 수량 합산(UPDATE) / 미존재 시 생성(INSERT). race 시 `@Retryable`로 재시도 → 사전 조회에서 기존 항목 발견 → 수량 합산 |
| 회원가입 | INSERT | UNIQUE constraint | `uk_active_login_id` 유니크. race 시 500 허용 |

### INSERT 중복 방어 구조

- **쿠폰 발급**: 1차 로컬 캐시(Caffeine) → 2차 DB 존재 확인(existsBy) → 3차 DB 복합 유니크 제약(안전망). race 시 500 허용
- **좋아요 행**: 사전 조회(findLike) → DB 복합 유니크 제약(안전망). race 시 500 허용 (멱등 연산, 부수 효과 미미)
- **장바구니 항목**: 사전 조회(findByUserIdAndProductId) → 존재 시 수량 합산(UPDATE) / 미존재 시 INSERT → DB 복합 유니크 제약(안전망). race 시 `@Retryable`이 `DataIntegrityViolationException`을 catch → 재시도 시 새 TX에서 사전 조회가 기존 항목 발견 → 수량 합산으로 정상 처리. 재시도 소진 시 `@Recover`에서 `CART_ADD_CONFLICT` 반환
- **회원가입**: 사전 조회(loginIdDuplicationCheck) → DB 유니크 제약(안전망). race 시 500 허용
- **주문 생성**: 사전 조회(findByUserIdAndRequestId) → DB 유니크 제약 위반 시 Facade에서 catch → 기존 주문 멱등 반환. **부수 효과(재고 차감, 장바구니 삭제, 쿠폰 사용)가 비가역적이므로 try-catch 유지**

### race 시 try-catch 유지 판단 기준

| 기준 | 500 허용 | `@Retryable` | try-catch 유지 |
|------|:--------:|:------------:|:-------------:|
| 부수 효과 없거나 미미 | O | | |
| 부수 효과 없으나 수량 합산 누락 위험 | | O | |
| 부수 효과가 크고 비가역적 | | | O |
| 사용자가 재시도 안 해도 무해 | O | | |
| 재시도로 자동 해소 가능 (sert-before-insert) | | O | |
| 사용자가 재시도 안 하면 데이터 불일치 | | | O |

---

## 3. 추후 개선 사항 및 확정된 의사결정

### 3-1. [TODO] 상품/브랜드 삭제 시 좋아요 정리 — 정리 제거 목표

**결정**: 상품/브랜드 삭제 시 좋아요 즉시 정리를 **제거**한다. 이벤트 기반 전환도 불필요하다.

**근거**:
- 상품/브랜드는 Soft Delete → 좋아요 조회 시 삭제된 리소스는 자동 필터링됨
- 고아 데이터(삭제된 상품/브랜드의 좋아요)는 무해함
- 복원 시 좋아요 데이터가 보존되어 데이터 손실 방지
- 필요 시 배치 잡으로 주기적 정리 가능

### 3-2. [TODO] 주문 생성 시 장바구니 정리 — Event 전환 목표

**현재**: `OrderPlacementCommandService`에서 장바구니 정리를 동기 호출

**개선 방향**: `OrderCreatedEvent` 발행 → `@TransactionalEventListener(phase = AFTER_COMMIT)`로 주문 TX 커밋 후 장바구니 정리 처리

**Event 패턴 상세**:
- 도메인 모델에서 이벤트 생성 (메서드가 이벤트를 return)
- `DomainEventPublisher`를 Port로 정의 (각 도메인의 `application/port/out/`)
- Service에서 Port를 통해 발행 (Facade는 Service만 호출하는 원칙 준수)
- `@TransactionalEventListener(phase = AFTER_COMMIT)` 사용 — 주문 TX 커밋 후 실행하여 장바구니 정리 실패가 주문을 롤백시키지 않음. Outbox pattern 미적용

### 3-3. [TODO] 상품 삭제 시 장바구니 정리 — 미확정 (논의 중)

**현재**: `ProductCommandFacade.deleteProduct()`에서 장바구니 전체 삭제가 동일 TX에 포함

**논의 포인트**: 삭제된 상품이 장바구니에 남아있으면 주문 시 상품 존재 검증 실패(404)가 발생하므로 좋아요보다 UX 영향이 큼. 이벤트 전환 vs 동기 유지 vs 정리 제거 검토 중.

### 3-4. ACL vs Event 선택 기준

| 통신 방식 | 선택 기준 | 예시 |
|-----------|----------|------|
| **ACL (동기)** | 결과값 필요, 실패 시 전체 롤백 필요 | 주문 시 재고 차감, 주문 시 쿠폰 적용, 장바구니 추가 시 상품 조회 |
| **Event** | 정리성 부수효과, 핵심 작업과 독립적 | 주문 생성 → 장바구니 정리 |
| **정리 제거** | Soft delete로 조회 시 자동 필터링 | 상품/브랜드 삭제 → 좋아요 정리 |
