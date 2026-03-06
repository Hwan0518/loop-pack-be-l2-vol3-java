> **⚠️ 히스토리 문서**: 이 문서는 낙관적 락(@Version + @Retryable)을 적용하기 위한 초기 설계 계획입니다. 이후 `06-optimistic-vs-pessimistic-vs-atomic-counter.md`에서 유스케이스별 재분석을 거쳐, 좋아요 수는 원자적 카운터로, 쿠폰 사용은 락 제거로 전략이 변경되었습니다.

# Round 4: 동시성 제어 리팩토링 계획

## 1. 배경

### 1.1 시스템 구조: 좋아요와 likeCount의 관계

이 프로젝트는 **Bounded Context(BC)** 단위로 모듈이 분리되어 있다.
좋아요 기능은 두 BC가 협력하여 동작한다.

```
[engagement BC]                          [catalog BC]
ProductLikeCommandFacade                 ProductLikeCountCommandFacade
  → ProductLikeCommandService              → ProductCommandService
    → ProductLike 저장 (likes 테이블)         → Product.likeCount 증감 (products 테이블)
```

- **engagement BC**: 좋아요 엔티티(`ProductLike`) 생성/삭제 담당
- **catalog BC**: 상품의 좋아요 수(`Product.likeCount`) 증감 담당

사용자가 좋아요를 누르면 아래 호출 체인이 **단일 트랜잭션** 안에서 실행된다.

```
ProductLikeCommandFacade.createLike()              [TX 시작 — engagement BC]
  → ProductLikeCommandService.createLike()         [like 저장]
  → ProductLikeCommandService.increaseLikeCount()  [Port 호출]
    → ProductLikeCountSyncer (Port 인터페이스)
      → ProductLikeCountSyncerImpl (ACL 구현체)     [Cross-BC 어댑터]
        → ProductLikeCountCommandFacade             [catalog BC]
          → ProductQueryService.findActiveById()    [상품 조회]
          → ProductCommandService.increaseLikeCount(product)  [likeCount + 1, 저장]
```

### 1.2 문제: Lost Update

현재 `ProductLikeCountCommandFacade`는 **락 없이** read-modify-write를 수행한다.

```java
// ProductLikeCountCommandFacade.java (현재 코드)
@Transactional
public void increaseLikeCount(Long productId) {
    Product product = productQueryService.findActiveById(productId);  // ① read
    productCommandService.increaseLikeCount(product);                 // ② modify + write
}

// ProductCommandService.java (현재 코드)
@Transactional
public void increaseLikeCount(Product product) {
    product.increaseLikeCount();    // likeCount++
    productCommandRepository.save(product);
}
```

두 사용자가 동시에 같은 상품에 좋아요를 누르면:

```
Thread A: read likeCount=10 → set 11 → save (성공)
Thread B: read likeCount=10 → set 11 → save (성공) ← Lost Update!
                                                       실제로는 12여야 하는데 11이 됨
```

반면 **재고 차감**(`ProductCommandService.decreaseStock()`)은 `findActiveByIdForUpdate()` (비관적 쓰기 락, `SELECT ... FOR UPDATE`)로 올바르게 동시성을 제어하고 있다.

### 1.3 분석 결과: 4가지 개선 포인트

`round4-docs/02-transaction-query-analysis.md`에서 도출된 개선 항목과, `03-sync-vs-event-analysis.md`에서 분석된 동기/비동기 전략을 종합한 결과:

| # | 항목 | 최종 목표 | 이번 구현 범위 |
|---|------|----------|--------------|
| 1 | **likeCount 동시성 제어** | 낙관적 락 (@Version) + 재시도 | **구현** |
| 2 | **OrderCommandFacade TX 분리** | 읽기를 TX 밖으로 분리 | 문서화만 (향후) |
| 3 | **Like Count 동기화 방식** | 이벤트 기반 비동기 (최종적 일관성) | 문서화만 (향후) |
| 4 | **Delete 부수효과 방식** | 이벤트 기반 비동기 (최종적 일관성) | 문서화만 (향후) |

이 문서는 **#1 likeCount 동시성 제어**의 구현 계획을 다룬다. 나머지는 부록(Section 7)에 문서화한다.

---

## 2. 설계 결정

### 2.1 동시성 제어 전략 선택 기준

동시성 문제에 대해 비관적 락과 낙관적 락 중 어떤 전략을 선택할지, **2축 판단 프레임워크**로 결정한다.

**판단 축:**
- **정합성 중요도**: 데이터 정확성이 비즈니스에 미치는 영향 (최상 / 상 / 중 / 하)
- **일관성 요구**: 실시간 반영이 필수인가, 최종적 일관성으로 충분한가

**정합성 중요도별 전략 선택 규칙:**

| 정합성 중요도 | 판단 질문 | 전략 |
|-------------|----------|------|
| **최상** | — | **비관적 락** (무조건) |
| **상** | 1초 내 동일 row에 5건 이상 동시 요청 가능한가? | Yes → **비관적 락** / No → **낙관적 락** |
| **중 이하** | — | **낙관적 락** |

**보조 원칙:**
- 실시간 일관성이 요구되더라도, **최대 3회 재시도(수십 ms)** 가 허용되면 낙관적 락 적용 가능
- **TX 범위 최소화**: 조회(read)는 TX 밖으로, 쓰기(write)만 TX로 감싸 DB 커넥션 점유 시간을 줄인다

### 2.2 프레임워크 적용: 재고(stock) vs 좋아요(likeCount)

위 프레임워크를 재고와 좋아요에 각각 적용한 decision trace:

**재고 (stock) → 비관적 락 (현행 유지)**

```
① 정합성 중요도: 최상
   - 재고 오차 → 초과 판매 → 주문 취소/매출 손실
   - 절대적 정확성 필수

② 판단: 정합성 "최상" → 비관적 락 (무조건)

③ 일관성 요구: 실시간
   - 플래시 세일 시 수백 TPS, 모든 요청이 반드시 성공해야 함
   - 대기(blocking)를 감수하더라도 순서 보장 필요

→ 결론: SELECT ... FOR UPDATE (비관적 쓰기 락)
```

**좋아요 (likeCount) → 낙관적 락 + 재시도**

```
① 정합성 중요도: 중
   - likeCount ±1 오차는 UX에 무영향
   - 비즈니스 크리티컬 데이터가 아님

② 판단: 정합성 "중" → 낙관적 락

③ 일관성 요구: 최종적 일관성
   - 좋아요 수는 정확한 실시간 반영보다 최종 정합이 중요
   - 충돌 시 재시도(수십 ms)로 충분히 해소 가능

→ 결론: @Version (낙관적 락) + 최대 3회 재시도
```

| 기준 | 재고 (stock) | 좋아요 (likeCount) |
|------|-------------|-------------------|
| 정합성 중요도 | 최상 (초과 판매 → 매출 손실) | 중 (±1 오차 무영향) |
| 일관성 요구 | 실시간 (즉시 반영 필수) | 최종적 일관성 (지연 허용) |
| 동시 충돌 빈도 | 높음 (플래시 세일 수백 TPS) | 낮~중 (인기 상품 간헐적) |
| 전략 | **비관적 락** — "기다려서라도 반드시 성공" | **낙관적 락** — "충돌하면 재시도" |

### 2.3 핵심 메커니즘: @Version + @Retryable

**@Version (JPA 낙관적 락)**
- `ProductEntity`에 `@Version Long version` 필드를 추가한다.
- JPA가 UPDATE 시 `WHERE version = ?` 조건을 자동으로 붙여, 다른 트랜잭션이 먼저 수정했으면 `OptimisticLockingFailureException`을 발생시킨다.

**@Retryable (재시도) — 낙관적 락 충돌 처리 1순위 패턴**

`OptimisticLockingFailureException`은 TX 커밋 시점에 발생한다. 따라서 Facade 내부의 try-catch로는 잡을 수 없다.

`@Retryable`은 `@Transactional` AOP **바깥**에서 프록시로 감싸므로, TX 커밋 시점 예외를 자연스럽게 catch하고 새 TX로 재시도한다. 재시도 소진 시 `@Recover` 메서드에서 도메인 전용 `CoreException`으로 변환한다.

```
@Retryable 프록시 (catch 가능 — TX 바깥)
  └─ @Transactional 프록시
       └─ 메서드 본문: read → modify → save()
       └─ TX 커밋 → OptimisticLockingFailureException 발생
  └─ @Retryable이 catch → 새 TX로 재시도
  └─ maxAttempts 소진 → @Recover 호출 → CoreException
```

**충돌 처리 패턴 우선순위:**

| 순위 | 패턴 | 사용 조건 | 사유 |
|------|------|----------|------|
| **1순위** | `@Retryable` + `@Recover` | 기본 (항상 이것부터 검토) | TX 커밋 시점 예외를 자연스럽게 처리, Spring AOP 의도대로 동작 |
| 2순위 | try-catch + `saveAndFlush()` | `@Retryable` 사용 불가 시에만 | 구조적 우회, 사유를 주석으로 명시 필수 |
| **금지** | try-catch + `save()` (flush 없음) | 사용 금지 | `save()`는 TX 커밋 전까지 SQL 미실행 → catch 불가 |

**REQUIRES_NEW (독립 트랜잭션)**
- `ProductCommandService.increaseLikeCount()`에 `@Transactional(propagation = REQUIRES_NEW)`를 적용한다.
- 외부 트랜잭션(좋아요 저장)과 독립된 새 트랜잭션에서 likeCount를 수정한다.
- 이렇게 해야 낙관적 락 충돌 시 외부 TX를 오염시키지 않고 재시도할 수 있다.

**재시도 흐름**
- Facade의 `@Retryable`이 `@Transactional` 바깥에서 재시도를 조율한다.
- 매 시도마다 새 트랜잭션이 열려 DB에서 최신 version을 다시 읽는다.

```
정상 흐름:
  시도 1: read(version=5) → likeCount++ → save(version=6) → 성공

충돌 후 재시도:
  시도 1: read(version=5) → likeCount++ → save → 실패 (다른 TX가 version=6으로 먼저 커밋)
  시도 2: read(version=6) → likeCount++ → save(version=7) → 성공
```

### 2.4 알려진 제약: REQUIRES_NEW와 원자성

REQUIRES_NEW를 사용하면 좋아요 저장(외부 TX)과 likeCount 증가(내부 TX)가 서로 다른 트랜잭션에서 실행된다. 이로 인해 **내부 TX가 커밋된 후 외부 TX가 롤백되면 데이터 불일치가 발생**할 수 있다.

**변경 후 호출 체인:**
```
ProductLikeCommandFacade.createLike()              [OUTER TX — like 저장]
  → ...
    → ProductLikeCountCommandFacade.increaseLikeCount() [TX 없음 — 재시도 조율]
      → ProductCommandService.increaseLikeCount()       [INNER TX (REQUIRES_NEW) — likeCount 증가]
```

**위험 시나리오:** Inner TX가 likeCount를 11로 커밋 → Outer TX가 커밋 실패(예: DB 커넥션 끊김) → like는 저장 안 됨, likeCount는 11

**수용 근거:**
1. `increaseLikeCount`는 `createLike()` 내 **마지막 연산**이다. Outer TX 커밋 실패(DB 커넥션 끊김 등)에서만 발생하며, 이는 극히 드물다.
2. 향후 목표(Section 7)가 **이벤트 기반 비동기**(최종적 일관성)이므로, 현재 단계에서도 동일한 일관성 수준을 수용한다.
3. likeCount는 비즈니스 크리티컬 데이터가 아니다 (좋아요 수 ±1 오차는 UX에 무영향).
4. **보정 전략**: 향후 배치 잡으로 `COUNT(product_likes WHERE target_id = ?)` vs `products.like_count` 정합성을 검증할 수 있다.

### 2.5 isNew() 동작 변경 주의

Spring Data JPA의 `SimpleJpaRepository.save()` 는 엔티티의 신규/기존 여부(`isNew()`)에 따라 `persist`(INSERT) 또는 `merge`(UPDATE)를 선택한다.

- **현재** (`@Version` 없음): `isNew()` = `id == null` (BaseEntity의 `@GeneratedValue(IDENTITY)` 기준)
- **변경 후** (`@Version Long version` 추가): `isNew()` = `version == null` (Spring Data가 `@Version` 필드를 우선 참조)

| 상황 | version 값 | isNew() | JPA 동작 | 정상 여부 |
|------|-----------|---------|---------|----------|
| 신규 상품 (`Product.create()`) | null | true | persist (INSERT) | 정상 |
| DB에서 복원 (`Product.reconstruct()`) | N (0 이상) | false | merge (UPDATE) | 정상 |
| reconstruct()에 version=null 전달 | null | true | persist → DuplicateKeyException | 명확한 실패 |

**방어 규칙:** `Product.reconstruct()`가 version 파라미터를 필수로 받도록 설계하여, DB 복원 시 version=null이 발생할 수 없게 한다.

---

## 3. 구현 상세

### Phase 1: @Version 필드 추가 (Entity + Domain + Mapper)

**ProductEntity.java** — `@Version` 컬럼 추가
```java
@Version
@Column(name = "version")
private Long version;
```
- `of(Long id, ..., Long version)` 팩토리 메서드에 version 파라미터 추가
- `of(Long brandId, ...)` (신규 생성용) 오버로드는 version = null (JPA가 persist 시 0으로 초기화)

**Product.java** — Domain 모델에 version 필드 추가
- `private Long version` (non-final, mutable — likeCount과 동일 패턴)
- `create()`: version = null (신규)
- `reconstruct()`: version 파라미터 추가 (DB 복원)

**ProductEntityMapper.java** — 양방향 version 매핑
- `toEntity()`: `product.getVersion()` → `ProductEntity.of(..., version)`
- `toDomain()`: `entity.getVersion()` → `Product.reconstruct(..., version, ...)`

### Phase 2: likeCount read-modify-write 통합 + 재시도

**ProductCommandService.java** — 시그니처 변경 + REQUIRES_NEW

현재 `increaseLikeCount(Product product)` → 변경 후 `increaseLikeCount(Long productId)`:
- 조회(read) + 수정(modify) + 저장(write)를 하나의 독립 트랜잭션 안에서 수행
- REQUIRES_NEW로 외부 TX와 격리 → 재시도 시 외부 TX에 영향 없음

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void increaseLikeCount(Long productId) {
    // 활성 상품 조회 (새 TX에서 최신 version 읽기)
    Product product = productQueryRepository.findActiveById(productId)
        .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));

    // 좋아요 수 증가
    product.increaseLikeCount();

    // 저장 (JPA @Version으로 낙관적 락 체크)
    productCommandRepository.save(product);
}
```
- `decreaseLikeCount()` 동일 패턴

**ProductLikeCountCommandFacade.java** — `@Retryable` + `@Recover`

```java
// @Retryable이 @Transactional 바깥에서 AOP 프록시로 감싸 TX 커밋 예외를 catch
@Retryable(
    retryFor = OptimisticLockingFailureException.class,
    recover = "recoverIncreaseLikeCountConflict",
    maxAttempts = 5,
    backoff = @Backoff(delay = 50, multiplier = 2, maxDelay = 200, random = true)
)
public void increaseLikeCount(Long productId) {
    productCommandService.increaseLikeCount(productId);
}

// 재시도 소진 시 도메인 전용 예외로 변환
@Recover
public void recoverIncreaseLikeCountConflict(OptimisticLockingFailureException e, Long productId) {
    throw new CoreException(ErrorType.PRODUCT_LIKE_INCREASE_CONFLICT);
}
```

변경 사항:
- 수동 재시도 루프 → `@Retryable` + `@Recover` 패턴으로 대체
- `@Retryable`이 `@Transactional` AOP 바깥에서 감싸므로 TX 커밋 시점 예외를 자연스럽게 catch
- `ProductQueryService` 의존성 제거 (Service가 내부에서 직접 조회)
- backoff: 지수 백오프 + 랜덤 지터로 동시 재시도 충돌 완화

**GlobalExceptionHandler.java** — 안전망 핸들러 추가

재시도 로직에서 잡히지 않는 낙관적 락 예외가 컨트롤러까지 전파될 경우를 대비한 글로벌 핸들러:

```java
// 낙관적 락 충돌 → 409 Conflict
@ExceptionHandler(OptimisticLockingFailureException.class)
public ResponseEntity<ErrorResponse> handleOptimisticLockException(OptimisticLockingFailureException e) {
    ErrorType errorType = ErrorType.LOCK_CONFLICT;
    ErrorResponse response = ErrorResponse.from(errorType);
    return ResponseEntity.status(errorType.getStatus()).body(response);
}
```

기존에 `PessimisticLockingFailureException` 핸들러가 동일 패턴으로 존재하므로, 낙관적 락 핸들러도 같은 구조로 추가.

### Phase 3: 시그니처 변경에 따른 호출자 업데이트

`Product.reconstruct()`와 `ProductEntity.of()`에 version 파라미터가 추가되므로, 이를 호출하는 모든 코드를 업데이트해야 한다.

**프로덕션 코드:**

| 파일 | 변경 내용 |
|------|----------|
| `ProductEntityMapper.java` | version 양방향 매핑 추가 |

**테스트 코드:**

| 파일 | 변경 내용 |
|------|----------|
| `ProductTest.java` | `reconstruct()` 호출에 version 파라미터 추가 |
| `ProductEntityMapperTest.java` | `reconstruct()` + `ProductEntity.of()` 호출 업데이트 |
| `ProductCommandServiceTest.java` | `increaseLikeCount(Long)` 시그니처에 맞춰 mock 대상 변경 |
| `ProductLikeCountCommandFacadeTest.java` | 재시도 테스트 추가, QueryService 의존성 제거 |
| `ProductQueryServiceTest.java` | 헬퍼 메서드의 `reconstruct()` 업데이트 |
| `ProductCommandFacadeTest.java` | 헬퍼 메서드의 `reconstruct()` 업데이트 |
| `ProductQueryFacadeTest.java` | 헬퍼 메서드의 `reconstruct()` 업데이트 |
| `OrderProductReaderImplTest.java` | `reconstruct()` 호출 업데이트 |
| `ProductQueryPortImplTest.java` | `ProductEntity.of()` 호출 확인 (id 없는 `of(brandId,...)` 오버로드 사용 — version=null이므로 변경 불필요할 수 있으나 확인 필요) |
| `GlobalExceptionHandlerTest.java` | 낙관적 락 핸들러 테스트 추가 |

### Phase 4: 테스트 작성

**단위 테스트:**

| 테스트 클래스 | 테스트 케이스 |
|-------------|-------------|
| `ProductTest` | `create()` → version이 null / `reconstruct()` → version 값 보존 |
| `ProductCommandServiceTest` | `increaseLikeCount(Long)` 정상 동작 / `decreaseLikeCount(Long)` 정상 동작 / 상품 없음 → PRODUCT_NOT_FOUND 예외 |
| `ProductLikeCountCommandFacadeTest` | 1회 성공 / `OptimisticLockingFailureException` 1회 후 재시도 성공 / MAX_RETRY 초과 → LOCK_CONFLICT 예외 |
| `GlobalExceptionHandlerTest` | `OptimisticLockingFailureException` → 409 LOCK_CONFLICT 응답 |

**동시성 통합 테스트:**

| 테스트 클래스 | 테스트 케이스 |
|-------------|-------------|
| `ProductLikeCountConcurrencyTest` (신규) | ExecutorService + CountDownLatch로 동시 좋아요 10건 → likeCount 정확히 10 검증 |

### Phase 5: DB 마이그레이션

```sql
ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```

| 환경 | 처리 방식 |
|------|----------|
| 테스트 (`ddl-auto: create`) | Hibernate가 `@Version` 기반으로 자동 생성 |
| 프로덕션 (`ddl-auto: none`) | 위 SQL로 수동 마이그레이션 필요 |

---

## 4. 수정 대상 파일 요약

### 프로덕션 코드 (6개)

| 파일 | 변경 |
|------|------|
| `catalog/product/infrastructure/entity/ProductEntity.java` | `@Version` 필드 추가, `of()` 팩토리 메서드 업데이트 |
| `catalog/product/domain/model/Product.java` | version 필드 추가, `reconstruct()` 시그니처 변경 |
| `catalog/product/infrastructure/mapper/ProductEntityMapper.java` | 양방향 version 매핑 |
| `catalog/product/application/service/ProductCommandService.java` | `increaseLikeCount`/`decreaseLikeCount` 시그니처 변경 + REQUIRES_NEW |
| `catalog/product/application/facade/ProductLikeCountCommandFacade.java` | 재시도 루프 추가, `@Transactional` 제거, `ProductQueryService` 의존성 제거 |
| `support/common/error/GlobalExceptionHandler.java` | `OptimisticLockingFailureException` 핸들러 추가 |

### 테스트 코드 (10개+)

| 파일 | 변경 |
|------|------|
| `ProductTest.java` | `reconstruct()` version 파라미터 추가 |
| `ProductEntityMapperTest.java` | version 매핑 테스트 |
| `ProductCommandServiceTest.java` | 시그니처 변경 반영 + mock 대상 변경 |
| `ProductLikeCountCommandFacadeTest.java` | 재시도 테스트 3종 추가, QueryService 제거 |
| `ProductQueryServiceTest.java` | 헬퍼 메서드 업데이트 |
| `ProductCommandFacadeTest.java` | 헬퍼 메서드 업데이트 |
| `ProductQueryFacadeTest.java` | 헬퍼 메서드 업데이트 |
| `OrderProductReaderImplTest.java` | `reconstruct()` 업데이트 |
| `GlobalExceptionHandlerTest.java` | 낙관적 락 핸들러 테스트 추가 |
| `ProductLikeCountConcurrencyTest.java` (신규) | 동시성 통합 테스트 |

---

## 5. 구현 순서 및 병렬 처리

| Phase | 작업 | 의존 관계 | 비고 |
|-------|------|----------|------|
| 1 | Entity + Domain + Mapper에 version 추가 | 없음 (기반 작업) | 최우선 |
| 2 | Service + Facade 리팩토링 (REQUIRES_NEW + 재시도) | Phase 1 필요 | Phase 1 직후 |
| 3 | 시그니처 변경에 따른 호출자(테스트) 업데이트 | Phase 1 필요 | Phase 2와 **병렬 가능** |
| 4 | 신규 테스트 작성 | Phase 1~3 필요 | 마지막 |
| 5 | DB 마이그레이션 SQL | 독립 | 언제든 가능 |

---

## 6. 검증 방법

1. **단위 테스트**: `./gradlew :apps:commerce-api:test` — 모든 기존 + 신규 테스트 통과
2. **동시성 테스트**: `ProductLikeCountConcurrencyTest` — 동시 좋아요 10건 후 likeCount = 10 검증
3. **수동 검증**: 두 개 터미널에서 동시에 좋아요 API 호출 → Lost Update 없이 정확한 카운트 확인

---

## 7. 쿠폰 발급 멱등성 제어

### 7.0 배경

`IssuedCouponCommandFacade.issueCoupon()`의 count → 비교 → save 패턴에서 동시 요청 시 중복 발급이 가능했다.
이를 이중 방어 전략으로 해결한다.

### 7.0.1 전략: 로컬 캐시(1차) + DB 유니크 제약(2차)

**1차 방어 — Caffeine 로컬 캐시**
- `CouponIssueIdempotencyCache` 인터페이스 (`application/port/out/cache/`)
- `CaffeineCouponIssueIdempotencyCache` 구현체 (`infrastructure/cache/`)
- key: `"userId:couponTemplateId"`, TTL 10분, 최대 10,000건
- `putIfAbsent` 기반 원자적 중복 차단 — 동일 JVM 내 동시 요청을 O(1)로 차단

**2차 방어 — DB 멱등성 키**
- `IssuedCoupon` 도메인에 `idempotencyKey` 필드 추가 (`"userId:couponTemplateId"`)
- `IssuedCouponEntity`에 `@Column(unique = true) idempotencyKey` 추가
- save() 시 `DataIntegrityViolationException` → `COUPON_ISSUE_DUPLICATED` 예외 변환
- 멀티 인스턴스 환경에서도 DB 레벨에서 완벽 차단

**동작 흐름:**
```
1. 사용자 인증
2. 로컬 캐시 tryAcquire(userId, templateId) → false면 즉시 COUPON_ISSUE_DUPLICATED
3. 템플릿 조회 + 발급 한도 검증
4. IssuedCoupon.create() → idempotencyKey 자동 생성
5. save() → DB 유니크 제약 위반 시 COUPON_ISSUE_DUPLICATED
```

**한계:**
- 1차 캐시는 단일 JVM 범위이므로, 멀티 인스턴스 환경에서는 2차 방어가 필수
- maxIssuePerUser > 1인 경우 idempotencyKey 전략 재설계 필요 (현재는 1인 1발급 가정)

---

## 8. 향후 목표 (현재 미구현)

이번 리팩토링에서는 다루지 않지만, 장기적으로 추진할 개선 항목:

### 8.1 OrderCommandFacade.createOrder() TX 분리

- **현재**: 인증 → 멱등성 검사 → 장바구니 조회 → 상품 조회 → 재고 차감 → 주문 생성 → 장바구니 정리가 단일 `@Transactional`
- **목표**: Facade에서 `@Transactional` 제거, 읽기 4건은 TX 없이 수행, 쓰기 3건(재고 차감 + 주문 생성 + 장바구니 정리)만 TX로 묶음
- **이유**: DB 커넥션 점유 시간 단축, Cross-BC ACL 호출이 쓰기 TX에 불필요하게 포함됨

### 8.2 Like Count 동기화 → 이벤트 기반

- **현재**: 좋아요 생성/삭제와 likeCount 증감이 동일 TX (동기, 이 계획에서 REQUIRES_NEW로 분리)
- **목표**: ProductLikeCreatedEvent / ProductLikeDeletedEvent 발행 → `@TransactionalEventListener(AFTER_COMMIT)` → likeCount 증감 (별도 TX)
- **이유**: 좋아요 카운트는 실시간성보다 최종적 일관성에 적합. 좋아요 저장 자체가 핵심이며, 카운트 반영은 지연 허용

### 8.3 Delete 부수효과 → 이벤트 기반

- **현재**: 상품/브랜드 삭제 시 좋아요/장바구니 정리가 동일 TX (동기)
- **목표**: ProductDeletedEvent / BrandDeletedEvent 발행 → 비동기 리스너에서 좋아요/장바구니 정리
- **이유**: 삭제는 관리자 작업(빈도 낮음), soft delete 필터링이 조회 시점에 이미 작동, 대량 정리 시 TX 길어짐 방지

### 8.4 기타 관련 개선

- **ProductLike 유니크 제약 추가**: `(user_id, target_type, target_id)` UNIQUE INDEX로 check-then-insert 레이스 완전 차단
- **likeCount 정합성 배치**: `COUNT(product_likes WHERE target_id = ?)` vs `products.like_count` 주기적 보정 배치 잡
