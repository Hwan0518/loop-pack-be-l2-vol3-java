# 05. 동시성 제어 전략

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
| 쿠폰 사용 | UPDATE | 락 불필요 | 1쿠폰 = 1사용자, 경합 자체가 성립하지 않음 |
| 쿠폰 발급 | INSERT | UNIQUE constraint | `(user_id, coupon_template_id)` 복합 유니크. race 시 500 허용 |
| 주문 생성 | INSERT | Idempotency key + try-catch | `(user_id, request_id)` 유니크. race 시 Facade catch → 멱등 반환 (부수 효과 비가역적) |
| 좋아요 행 | INSERT | UNIQUE constraint | `(user_id, target_type, target_id)` 복합 유니크. race 시 500 허용 |
| 장바구니 항목 | INSERT | UNIQUE constraint | `(user_id, product_id)` 복합 유니크. race 시 500 허용 |
| 회원가입 | INSERT | UNIQUE constraint | `uk_active_login_id` 유니크. race 시 500 허용 |

### INSERT 중복 방어 구조

- **쿠폰 발급**: 1차 로컬 캐시(Caffeine) → 2차 DB 존재 확인(existsBy) → 3차 DB 복합 유니크 제약(안전망). race 시 500 허용
- **좋아요 행**: 사전 조회(findLike) → DB 복합 유니크 제약(안전망). race 시 500 허용 (멱등 연산, 부수 효과 미미)
- **장바구니 항목**: 사전 조회(findByUserIdAndProductId) → DB 복합 유니크 제약(안전망). race 시 500 허용
- **회원가입**: 사전 조회(loginIdDuplicationCheck) → DB 유니크 제약(안전망). race 시 500 허용
- **주문 생성**: 사전 조회(findByUserIdAndRequestId) → DB 유니크 제약 위반 시 Facade에서 catch → 기존 주문 멱등 반환. **부수 효과(재고 차감, 장바구니 삭제, 쿠폰 사용)가 비가역적이므로 try-catch 유지**

### race 시 try-catch 유지 판단 기준

| 기준 | 500 허용 | try-catch 유지 |
|------|:--------:|:-------------:|
| 부수 효과 없거나 미미 | O | |
| 부수 효과가 크고 비가역적 | | O |
| 사용자가 재시도 안 해도 무해 | O | |
| 사용자가 재시도 안 하면 데이터 불일치 | | O |

---

## 3. 추후 개선 사항

### 상품 삭제 시 부수효과를 이벤트로 분리

**현재**: `ProductCommandFacade.deleteProduct()`에서 Catalog BC(상품 삭제) + Engagement BC(좋아요 전체 삭제) + Cart BC(장바구니 전체 삭제)가 하나의 TX에 포함

**개선 방향**: 상품 삭제 후 `ProductDeletedEvent`를 발행하고, 좋아요/장바구니 정리를 `@TransactionalEventListener`로 분리

**현재 미적용 사유**:
- 관리자 전용 기능으로 트래픽이 매우 낮아 TX 크기로 인한 성능 문제가 없음
- soft delete이므로 부수효과가 일시적으로 지연되어도 조회 시 필터됨

**적용 시점**: BC 경계를 엄격히 분리해야 하는 시점 (서비스 분리, 독립 배포 등)
