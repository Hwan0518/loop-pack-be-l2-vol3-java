# 동시성 제어 전략 재설계 — 낙관적 락 vs 비관적 락 vs 원자적 카운터

## 이 문서가 풀고자 하는 문제

**본질적 문제**: 동시성 제어는 "기술을 아는 것"과 "올바르게 적용하는 것" 사이에 큰 간극이 있다. 낙관적 락, 비관적 락, 원자적 카운터 — 각 기술의 작동 원리를 아는 것만으로는 부족하다. 핵심은 **"이 유스케이스의 비즈니스 특성이 어떤 전략을 요구하는가"**를 판단하는 것이다. 기술이 아닌 비즈니스에서 출발하지 않으면, 높은 경합에 낙관적 락을 걸거나, INSERT에 `@Version`을 붙이려 하거나, 단순 카운터에 도메인 모델 전체를 로딩하는 식의 **기술-비즈니스 불일치**가 발생한다.

**구체적 문제**: 동시성 제어 전략을 유스케이스별 특성을 분석하지 않고 일괄 적용한 결과, 맞지 않는 옷을 입힌 곳에서 실질적인 문제가 발생했다.

- 좋아요 수: 높은 경합에 낙관적 락을 적용 → **동시성 테스트 실패 (기대 10, 실제 7)**
- 좋아요 행: 유니크 제약 없이 check-then-create → **중복 데이터 가능**
- 쿠폰 발급: INSERT에 count 기반 검증 → **race condition으로 중복 발급 가능**
- 주문 멱등성: 별도 테이블로 과잉 설계 → **14개 파일의 불필요한 복잡도**

**기대 효과:**

| 영역 | Before | After |
|------|--------|-------|
| **정합성** | 좋아요 수 동시성 실패 (7/10) | 원자적 카운터로 100% 정확한 카운트 |
| **데이터 무결성** | 좋아요/쿠폰 중복 데이터 가능 | DB 유니크 제약으로 불가능한 상태 제거 |
| **UX (응답 속도)** | 좋아요에 낙관적 락 재시도 → 지연/실패 | 단일 SQL로 즉시 반환 |
| **도메인 응집도** | 멱등키가 별도 도메인으로 분산 | Order가 자신의 requestId를 직접 소유 |
| **코드 복잡도** | 14개 IdempotencyKey 파일 + ProductLikeCountCommandFacade | ~17개 파일 삭제, 책임 단순화 |
| **네이밍 정확성** | 쿠폰의 UNIQUE constraint를 "idempotencyKey"로 오명명 | 비즈니스 의도에 맞는 네이밍 (복합 유니크 제약) |
| **아키텍처 일관성** | 유스케이스마다 제각각 전략 | 의사결정 트리 기반의 일관된 선택 기준 |

---

## **Part 0: 문제 발견 — 어떤 상황에서 시작했나**

낙관적 락 + `@Retryable` 패턴을 적용하고, 동시성 테스트를 돌려보니 실패했다.
`expected: 10L but was: 7L`. 재시도 횟수를 5회로 올려도, 10개 스레드가 동시에 같은 row를 치면 버전 충돌이 연쇄적으로 터진다.

여기서 의문이 들었다. "낙관적 락이 이 유스케이스에 맞는 옷인가?"
이 질문에서 시작해, 프로젝트의 모든 동시성 제어 포인트를 다시 점검했다.

---

### **0-1. 좋아요 수 — 동시성 테스트 실패**

10개 스레드가 동시에 좋아요를 누르면, 기대값 10인데 실제 결과는 7이었다.

현재 코드 구조는 이렇다.

**`ProductLikeCountCommandFacade`** — 낙관적 락 재시도 조율:

```java
@Retryable(
    retryFor = OptimisticLockingFailureException.class,
    recover = "recoverIncreaseLikeCountConflict",
    maxAttempts = 5,
    backoff = @Backoff(delay = 50, multiplier = 2, maxDelay = 200, random = true)
)
public void increaseLikeCount(Long productId) {
    productCommandService.increaseLikeCount(productId);
}
```

**`ProductCommandService`** — REQUIRES_NEW로 독립 TX에서 read-modify-write:

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

**`Product`** — 도메인 모델에서 단순 증가:

```java
// 8. 좋아요 수 증가
public void increaseLikeCount() {
    this.likeCount++;
}
```



**왜 실패하는가?**

10개 스레드가 동시에 `findActiveById()`를 호출하면, 모두 같은 `version=0`을 읽는다.
첫 번째 스레드만 `WHERE version = 0`에 성공하고, 나머지 9개는 `OptimisticLockingFailureException`을 받는다.

재시도 2회차에서 살아남은 9개 중 1개만 성공하고, 8개가 다시 실패한다.
이런 식으로 5회 재시도가 소진되면, 마지막까지 성공하지 못한 스레드들은 `@Recover`로 빠진다.

결론부터 말하면, **경합이 높은 곳에 낙관적 락은 맞지 않는 옷이다.**

좋아요 수 증가의 비즈니스 로직을 보면 `this.likeCount++` — 단 한 줄이다. 검증도 없고, 다른 필드와의 의존도 없다. 이 단순한 연산을 위해 도메인 모델 전체를 로딩하고, `@Version` 충돌을 감수하고, 재시도 루프를 돌리는 것은 과잉이다.

---

### **0-2. 좋아요 행 — 중복 생성 가능성**

좋아요 행(row) 자체에도 문제가 있다. 현재 `ProductLikeCommandFacade.createLike()`의 흐름을 보면:

```java
// 1. 상품 좋아요 생성 (멱등)
@Transactional
public ProductLikeOutDto createLike(String loginId, String password, Long targetId) {

    // 사용자 인증
    Long userId = productLikeCommandService.authenticate(loginId, password);

    // 기존 좋아요 존재 시 기존 반환 (멱등)
    Optional<ProductLike> existing = productLikeCommandService.findLike(userId, targetId);
    if (existing.isPresent()) {
        return ProductLikeOutDto.from(existing.get());
    }

    // 좋아요 생성
    ProductLike productLike = productLikeCommandService.createLike(userId, targetId);

    // 좋아요 수 증가 (Cross-BC 부수효과)
    productLikeCommandService.increaseLikeCount(targetId);

    // DTO 변환
    return ProductLikeOutDto.from(productLike);
}
```

전형적인 **check-then-create** 패턴이다.

두 요청이 동시에 `findLike()`를 호출하면, 둘 다 `empty`를 받고, 둘 다 `createLike()`를 실행한다.
좋아요 테이블에 `(user_id, target_type, target_id)` 유니크 제약이 없으므로, **같은 사용자가 같은 상품에 2개의 좋아요를 갖게 된다.**

---

### **0-3. 쿠폰 발급 — race condition과 네이밍 문제**

`IssuedCouponCommandFacade.issueCoupon()`을 보면:

```java
@Transactional
public CouponIssueOutDto issueCoupon(String loginId, String password, Long couponTemplateId) {

    // 사용자 인증
    Long userId = issuedCouponCommandService.authenticate(loginId, password);

    // 1차 방어: 로컬 캐시로 중복 발급 빠르게 차단
    issuedCouponCommandService.validateNotDuplicateIssue(userId, couponTemplateId);

    // 쿠폰 템플릿 조회 (만료/삭제 검증 포함)
    CouponTemplate template = couponTemplateQueryService.getById(couponTemplateId);

    // 발급 한도 검증
    if (template.getMaxIssuePerUser() != null) {
        long issuedCount = issuedCouponQueryService.countByUserIdAndTemplateId(userId, couponTemplateId);
        if (issuedCount >= template.getMaxIssuePerUser()) {
            throw new CoreException(ErrorType.COUPON_ISSUE_LIMIT_EXCEEDED);
        }
    }

    // 발급 쿠폰 생성 및 저장 (2차 방어: DB 유니크 제약 — idempotencyKey)
    try {
        IssuedCoupon issuedCoupon = IssuedCoupon.create(couponTemplateId, userId);
        IssuedCoupon saved = issuedCouponCommandService.save(issuedCoupon);
        return CouponIssueOutDto.from(saved);
    } catch (DataIntegrityViolationException e) {
        throw new CoreException(ErrorType.COUPON_ISSUE_DUPLICATED);
    }
}
```

이전 라운드에서 Caffeine 캐시(1차) + DB 유니크 제약(2차) 이중 방어를 추가했다. DB 유니크 제약 덕분에 실제 중복 발급은 불가능하다. 하지만 두 가지 문제가 남아 있다.

**문제 1: count 기반 검증이 여전히 존재한다.**

```java
long issuedCount = issuedCouponQueryService.countByUserIdAndTemplateId(userId, couponTemplateId);
if (issuedCount >= template.getMaxIssuePerUser()) { ... }
```

이 검증은 `count → compare → save`의 3단계가 원자적이지 않다. DB 유니크 제약이 최종 방어선이니 중복 발급 자체는 막히지만, 이 count 검증은 **불필요한 DB 조회**이다. 유니크 제약이 이미 1인 1발급을 보장하므로, count 로직은 제거할 수 있다.

**문제 2: "idempotencyKey"라는 네이밍이 부정확하다.**

```java
// IssuedCoupon.java
String idempotencyKey = userId + ":" + couponTemplateId;
```

이 키의 실체는 `(userId, couponTemplateId)` 복합 유니크 제약이다. **"같은 사용자가 같은 쿠폰을 두 번 발급받을 수 없다"**는 비즈니스 규칙의 구현이지, 네트워크 재전송을 방어하는 멱등성 키가 아니다.

멱등성 키는 "같은 요청이 두 번 전송되었을 때 한 번만 처리"하기 위한 것이다. 쿠폰 발급의 경우, 비즈니스 규칙 자체가 중복을 금지하므로 별도의 멱등성 키가 필요 없다. `(user_id, coupon_template_id)` 복합 유니크 제약만으로 충분하다.

---

### **0-4. 주문 멱등성 키 — 별도 테이블의 과잉 설계**

주문 생성의 멱등성을 위해 `IdempotencyKey`가 별도 도메인 모델로 존재한다.

```java
// IdempotencyKey.java — 도메인 모델
@Getter
public class IdempotencyKey {
    private final Long id;
    private final Long userId;
    private final String requestId;
    private final Long orderId;
    private final LocalDateTime createdAt;
    // ...
}
```

```java
// IdempotencyKeyEntity.java — JPA 엔티티
@Entity
@Table(name = "idempotency_keys", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "request_id"})
})
public class IdempotencyKeyEntity extends BaseEntity {
    private Long userId;
    private String requestId;
    private Long orderId;
}
```

이 구조를 지탱하는 파일이 **14개**다.

| 구분 | 파일 |
|------|------|
| 도메인 모델 | `IdempotencyKey.java` |
| 엔티티 | `IdempotencyKeyEntity.java` |
| JPA | `IdempotencyKeyJpaRepository.java` |
| 매퍼 | `IdempotencyKeyEntityMapper.java` |
| Repository (I) | `IdempotencyKeyCommandRepository.java`, `IdempotencyKeyQueryRepository.java` |
| Repository (Impl) | `IdempotencyKeyCommandRepositoryImpl.java`, `IdempotencyKeyQueryRepositoryImpl.java` |
| Service | `OrderIdempotencyQueryService.java` (Facade에서 호출) |
| 테스트 | `IdempotencyKeyTest.java`, `IdempotencyKeyEntityMapperTest.java`, `IdempotencyKeyCommandRepositoryTest.java`, `IdempotencyKeyQueryRepositoryTest.java` |

`OrderCommandService.execute()`에서 주문 저장 후 멱등성 키를 별도로 저장한다:

```java
// 멱등성 키 저장 (동시 요청 race 시 DataIntegrityViolationException → TX 롤백 → Facade에서 기존 주문 반환)
IdempotencyKey idempotencyKey = IdempotencyKey.create(userId, inDto.requestId(), savedOrder.getId());
idempotencyKeyCommandRepository.save(idempotencyKey);
```

그런데 이 멱등성 키는 **주문 생성에서만 사용된다.** TTL도 없고, 다른 API에서 재사용하지도 않는다. 범용 멱등성 프레임워크를 만들 계획도 없다.

`Order` 도메인 모델에 `requestId` 필드를 추가하고, `OrderEntity`에 `(user_id, request_id)` 유니크 제약을 걸면 **14개 파일이 삭제된다.** Order가 자신의 멱등성을 직접 소유하는 것이 도메인 응집도 측면에서도 올바르다.

---

### **0-5. 정상 동작 중인 부분 (비교 대상)**

모든 곳에 문제가 있는 것은 아니다. 올바르게 적용된 케이스를 확인하고, "왜 올바른지"를 분석해야 잘못된 곳의 개선 방향도 보인다.

**재고 차감 — 비관적 락 (`SELECT ... FOR UPDATE`)**

```java
// 6. 상품 재고 차감 (비관적 쓰기 락)
@Transactional
public void decreaseStock(Long productId, Long quantity) {

    // 활성 상품 조회 (비관적 쓰기 락 — 동시 재고 차감 경합 방지)
    Product product = productQueryRepository.findActiveByIdForUpdate(productId)
        .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));

    // 재고 차감 (도메인 로직 — 재고 부족 시 PRODUCT_OUT_OF_STOCK 예외)
    product.decreaseStock(quantity);

    // 재고가 차감된 상품 저장
    productCommandRepository.save(product);
}
```

비관적 락이 적합한 이유:
- `Stock` VO에 재고 부족 검증(`stock >= quantity`)이라는 **복잡한 비즈니스 로직**이 있다.
- 플래시 세일 시 수백 TPS — **경합이 매우 높다.**
- 초과 판매는 주문 취소/매출 손실 — **정확성이 절대적으로 중요하다.**
- 사용자가 "결제" 버튼을 누른 상황이므로 **조금 기다려도 정확한 결과를 기대한다.**

동시성 테스트 결과: 10개 스레드가 동시에 1개씩 차감해도 재고가 정확히 90으로 떨어진다. 재고가 5개인 상품에 10개 요청이 들어오면, 5개 성공 + 5개 `PRODUCT_OUT_OF_STOCK` — 정확하다.

**쿠폰 사용 — 과잉 방어 (이후 제거)**

```java
// IssuedCouponCommandFacade.java (당시 코드 — 이후 락 전체 제거)
@Retryable(
    retryFor = OptimisticLockingFailureException.class,
    recover = "recoverApplyToCouponConflict",
    maxAttempts = 3,
    backoff = @Backoff(delay = 50, multiplier = 2, maxDelay = 200, random = true)
)
public CouponApplyResult applyToCoupon(Long issuedCouponId, Long userId, BigDecimal totalPrice) { ... }
```

처음에는 "복잡한 비즈니스 로직이 있으니 낙관적 락이 적합하다"고 판단했다. 하지만 이후 더 근본적인 질문을 던지게 되었다: **"애초에 동시성 경합이 현실적으로 발생하는가?"**

- 1장의 쿠폰은 1명의 사용자만 소유한다.
- 같은 사용자가 같은 쿠폰으로 동시에 2개 주문을 넣는 시나리오는 사실상 없다.
- 즉, **경합 자체가 성립하지 않는다.** 경합이 없는 곳에 락을 거는 것은 과잉 방어다.

→ 이후 Section C에서 "락 불필요" 결론으로 변경. `@Version`, `@Retryable`, `@Recover` 전체 제거.

---

## **Part 1: 동시성 제어 방법의 선택 기준**

Part 0에서 발견한 문제들을 정리하면, 결국 **"어떤 유스케이스에 어떤 전략을 써야 하는가"**에 대한 판단 기준이 필요하다. UPDATE와 INSERT는 동시성 문제의 성격이 완전히 다르므로, 각각 별도의 의사결정 트리가 필요하다.

---

### **1-1. 기존 행 UPDATE에 대한 세 가지 방법**

| 기준 | 낙관적 락 (`@Version`) | 비관적 락 (`FOR UPDATE`) | 원자적 카운터 (`UPDATE SET col = col + 1`) |
|------|----------------------|------------------------|----------------------------------------|
| **핵심 메커니즘** | 읽기 → 검증 → 쓰기. 커밋 시 version 불일치면 실패 | 읽기 시점에 row 잠금. 다른 TX 대기 | DB가 읽기+쓰기를 단일 SQL로 처리 |
| **적합한 경합 수준** | 낮음 (충돌 거의 없을 때) | 높음 (동시 접근 빈번할 때) | 무관 (경합 수준에 상관없이 동작) |
| **비즈니스 로직** | 복잡한 검증/계산이 필요할 때 | 복잡한 검증/계산 + 높은 경합 | 단순 증감만 (검증 불필요) |
| **객체지향 적합성** | 높음 (도메인 모델에 로직 캡슐화) | 높음 (도메인 모델에 로직 캡슐화) | 낮음 (도메인 모델 우회) |
| **실패 처리** | 재시도 필요 (`@Retryable`) | 데드락 가능 (락 순서 관리 필요) | 실패 없음 (단일 SQL) |
| **UX (응답 속도)** | 빠름 (충돌 없을 때) / 재시도 시 지연 | 대기 시간 존재 (락 해제까지) | 가장 빠름 (단일 SQL, 재시도 없음) |



> **UX 관점의 핵심 기준**: "클라이언트(사용자)가 빠른 응답을 기대하는가?"
> - 사용자가 즉각적인 피드백을 기대하는 동작(좋아요, 장바구니 등) → 낙관적 락 또는 원자적 카운터
> - 조금 기다려도 정확성이 중요한 동작(재고 차감, 결제 등) → 비관적 락
>
> 요즘 들어 웹 개발자는 'UX'를 파는 직업이라는 생각이 든다. 그래서 이력서에도 '기술'만을 고집하지 않고 '서비스'를 이해하려고 한다는 문구를 적어놓는다. 그런데 이번에 낙관적 락 vs 비관적 락을 고민하던 도중, 멘토님의 선택 기준이 '클라이언트'임을 듣고 또다시 'UX'의 중요성을 깨닫게 되었다.



**의사결정 트리 (UPDATE):**

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
                  └── No (정확성 중요 + 경합 높음 또는 읽기→검증→쓰기 원자성 필수)
                        └── 비관적 락
```

> Q0이 가장 먼저 오는 이유: "복잡한 상태 변경이 있으니 일단 락을 걸자"는 사고방식이 과잉 방어로 이어질 수 있다. 쿠폰 사용이 그 대표적 사례다. 비즈니스 로직이 복잡하더라도, **경합이 성립하지 않으면 락은 불필요하다.**

---

### **1-2. INSERT에 대한 두 가지 방법**

INSERT에서의 동시성 문제는 UPDATE와 본질이 다르다. UPDATE는 "같은 row를 누가 먼저 수정하느냐"의 문제이고, INSERT는 **"이 row가 생겨도 되는가"**의 문제다.

INSERT 중복 방어에도 두 가지 유형이 있다.

| 기준 | UNIQUE constraint (복합 유니크) | Idempotency key (멱등성 키) |
|------|-------------------------------|---------------------------|
| **목적** | 비즈니스 규칙 위반 방지 | 네트워크 재전송 방어 |
| **키 도출** | 비즈니스 식별자 조합 (예: user_id + coupon_template_id) | 클라이언트가 생성한 고유 ID (예: UUID requestId) |
| **중복 시 응답** | 에러 (비즈니스 규칙 위반이므로) | 기존 결과 반환 (동일 요청이므로) |
| **예시** | "같은 사용자가 같은 쿠폰을 2번 발급받을 수 없다" | "같은 주문 요청이 네트워크 문제로 2번 전송되었다" |



**의사결정 트리 (INSERT):**

```
Q1. 중복의 원인이 무엇인가?
  ├── 비즈니스 규칙 위반 (같은 걸 두 번 하면 안 됨)
  │     └── UNIQUE constraint
  └── 네트워크 문제 재전송 (같은 요청이 두 번 도착)
        └── Idempotency key
```

핵심 차이는 **"중복이 발생했을 때 에러인가, 정상인가"**이다.
- 쿠폰 2번 발급: 비즈니스 규칙 위반 → **에러**
- 주문 요청 2번 도착: 네트워크 문제 → **기존 주문 반환 (정상)**

---

## **Part 2: 유스케이스별 분석**

Part 1의 의사결정 트리를 각 유스케이스에 적용한다.

---

### **A: 좋아요 수 (UPDATE) → 원자적 카운터**

**현재 구현과 문제:**

```
ProductLikeCountCommandFacade (@Retryable, maxAttempts=5)
  → ProductCommandService (REQUIRES_NEW)
    → findActiveById()      ← 도메인 모델 전체 로딩
    → product.increaseLikeCount()  ← this.likeCount++
    → productCommandRepository.save()  ← @Version 체크
```

동시성 테스트 결과: `expected: 10L but was: 7L`

**의사결정 트리 적용:**

```
Q1. 단순 증감(+1/-1)이고, 복잡한 비즈니스 검증이 없는가?
  → Yes. product.increaseLikeCount()의 구현은 this.likeCount++ 한 줄이다.
    재고처럼 "부족하면 예외"같은 검증도 없다.
  → 원자적 카운터
```

**원자적 카운터가 적합한 이유:**

1.  **비즈니스 로직이 없다.** `this.likeCount++`에는 검증도 계산도 없다. 도메인 모델을 로딩할 이유가 없다.
2.  **경합이 높다.** 인기 상품은 동시에 수십~수백 명이 좋아요를 누른다.
3.  **UX 관점에서 즉시 응답이 필요하다.** 좋아요 버튼을 누르고 재시도 지연을 기다리는 UX는 없다.
4.  **단일 SQL로 해결된다.** `UPDATE products SET like_count = like_count + 1 WHERE id = ?` — DB가 원자성을 보장한다.

**확장 시나리오 — 비즈니스 검증이 추가된다면?**

"좋아요 수가 10,000 이상이면 더 이상 증가하지 않는다"같은 요구사항이 생기면?
→ 원자적 카운터 + WHERE 조건으로 처리: `UPDATE ... SET like_count = like_count + 1 WHERE id = ? AND like_count < 10000`
→ 도메인 모델 로딩 없이 DB 레벨에서 검증 가능.

만약 여러 필드를 복합적으로 검증해야 한다면(예: "좋아요 수 + 리뷰 수 합산이 N 이하일 때만")? 그때 비관적 락으로 전환한다. 하지만 현재는 그런 요구사항이 없다.

**최종 결정: 원자적 카운터**

- `ProductLikeCountCommandFacade` 삭제 (또는 단순 위임으로 축소)
- `ProductCommandService.increaseLikeCount()`가 `@Retryable`, `REQUIRES_NEW` 없이 단일 UPDATE SQL 실행
- QueryDSL 또는 `@Modifying` + `@Query`로 `UPDATE products SET like_count = like_count + 1 WHERE id = ?` 실행

---

### **B: 재고 차감 (UPDATE) → 비관적 락 유지**

**의사결정 트리 적용:**

```
Q1. 단순 증감이고, 복잡한 비즈니스 검증이 없는가?
  → No. Stock VO에 재고 부족 검증(stock >= quantity)이 있다.
    → Q2로 이동.

Q2. 빠른 응답 기대 + 경합 낮음?
  → No. 플래시 세일 시 수백 TPS + 정확성이 절대적으로 중요.
    → 비관적 락.
```

현재 구현이 올바르다. 변경 불필요.

---

### **C: 쿠폰 사용 (UPDATE) → 락 불필요 (과잉 방어 제거)**

**의사결정 트리 적용:**

```
Q0. 애초에 동시성 경합이 현실적으로 발생하는가?
  → No. 1장의 쿠폰은 1명의 사용자만 소유한다.
    같은 사용자가 동시에 2개 주문을 넣어 같은 쿠폰을 사용하는 시나리오는 사실상 없다.
    → 락 불필요.
```

**왜 처음에 낙관적 락을 적용했는가:**

"쿠폰 상태 변경(AVAILABLE → USED) + 만료 검증 + 소유자 검증 + 할인 계산"이라는 복잡한 비즈니스 로직이 있어서, "복잡한 상태 변경이니 락이 필요하다"고 판단했다. 하지만 이것은 **기술적 복잡도에서 출발한 판단**이었다.

**왜 제거했는가:**

더 근본적인 질문은 "이 비즈니스 로직이 동시에 실행될 가능성이 있는가?"였다. 1장의 쿠폰 = 1명의 사용자이므로, 같은 row에 대한 동시 수정이 발생할 조건 자체가 성립하지 않는다. 발생하지 않는 문제를 막기 위해 `@Version` + `@Retryable` + `@Recover`를 올리는 것은 과잉 방어였다.

**최종 결정: 락 전체 제거**

- `IssuedCoupon` 도메인: `version` 필드 삭제
- `IssuedCouponEntity`: `@Version` 컬럼 삭제
- `IssuedCouponCommandFacade.applyToCoupon()`: `@Retryable`, `@Recover` 삭제 → 평범한 메서드 위임
- `ErrorType.COUPON_CONCURRENT_USE` 삭제

---

### **D: 쿠폰 발급 (INSERT, 중복 데이터) → UNIQUE constraint (복합 유니크)**

**현재 구현:**

```
1차 방어: Caffeine 로컬 캐시 (validateNotDuplicateIssue)
2차 방어: count 기반 검증 (countByUserIdAndTemplateId >= maxIssuePerUser)
3차 방어: DB 유니크 제약 (idempotency_key 컬럼, UNIQUE)
```

**의사결정 트리 적용:**

```
Q1. 중복의 원인이 무엇인가?
  → 비즈니스 규칙 위반. "같은 사용자가 같은 쿠폰을 두 번 발급받을 수 없다."
    → UNIQUE constraint.
```

**개선 내용:**

1.  **`idempotencyKey` 컬럼 제거** → `(user_id, coupon_template_id)` 복합 유니크 제약으로 대체.
    - `IssuedCoupon` 도메인 모델에서 `idempotencyKey` 필드 삭제
    - `IssuedCouponEntity`에서 `idempotency_key` 컬럼 삭제
    - `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "coupon_template_id"}))` 추가

2.  **count 기반 검증 제거 + `maxIssuePerUser` 필드 삭제.** 1인 1쿠폰 정책을 DB 복합 유니크 제약이 보장하므로, `countByUserIdAndTemplateId` 조회와 `CouponTemplate.maxIssuePerUser` 필드 자체가 불필요하다. 필드, 생성자 파라미터, getter, setter 모두 삭제.

3.  **캐시 네이밍 변경.** `CouponIssueIdempotencyCache` → `CouponIssueDuplicateGuard`로 변경. 비즈니스 의도(중복 방지)에 맞는 이름.

4.  **try-catch 제거.** `@Transactional` 내에서 `DataIntegrityViolationException`을 catch하면 TX가 rollback-only 상태가 되어 정상 동작하지 않는다. 로컬 캐시가 99.9%의 중복 요청을 차단하고, DB 유니크 제약은 데이터 무결성의 silent safety net 역할만 한다.

**개선 후 흐름:**

```
1차 방어: 로컬 캐시 (Caffeine — 따닥 등 대부분의 중복 요청을 빠르게 차단)
2차 방어: DB 존재 확인 (existsByUserIdAndCouponTemplateId — 캐시 TTL 만료 후 재요청 방어)
3차 방어: DB 복합 유니크 제약 (user_id, coupon_template_id — 데이터 무결성 안전망)
  → 0.01% 레이스 시 500 → 클라이언트 재시도 → 캐시 또는 DB 존재 확인에서 중복 차단
```

count 검증과 `maxIssuePerUser`가 사라지고, DB 존재 확인이 캐시 TTL 만료 시나리오를 커버하면서 불필요한 500 응답을 방지한다. 네이밍도 비즈니스 의도와 일치한다.

---

### **E: 주문 생성 (INSERT, 중복 요청) → Idempotency key (requestId를 Order에 통합)**

**현재 구현:**

```
IdempotencyKey (별도 도메인 모델)
  → IdempotencyKeyEntity (별도 테이블)
    → (user_id, request_id) UNIQUE constraint
```

14개 파일. 주문 생성에서만 사용.

**의사결정 트리 적용:**

```
Q1. 중복의 원인이 무엇인가?
  → 네트워크 문제 재전송. 클라이언트가 주문 요청을 보냈는데 응답을 못 받아 재전송.
    → Idempotency key. 하지만 별도 테이블이 아니라 Order에 통합.
```

**개선 방향:**

1.  `Order` 도메인 모델에 `requestId` 필드 추가.
2.  `OrderEntity`에 `(user_id, request_id)` 복합 유니크 제약 추가.
3.  `IdempotencyKey` 관련 14개 파일 삭제:
    - `IdempotencyKey.java`, `IdempotencyKeyEntity.java`, `IdempotencyKeyJpaRepository.java`
    - `IdempotencyKeyEntityMapper.java`
    - `IdempotencyKeyCommandRepository.java`, `IdempotencyKeyQueryRepository.java`
    - `IdempotencyKeyCommandRepositoryImpl.java`, `IdempotencyKeyQueryRepositoryImpl.java`
    - `OrderIdempotencyQueryService.java` (Facade에서 직접 조회로 대체)
    - `IdempotencyKeyTest.java`, `IdempotencyKeyEntityMapperTest.java`
    - `IdempotencyKeyCommandRepositoryTest.java`, `IdempotencyKeyQueryRepositoryTest.java`

4.  `OrderCommandFacade.createOrder()`에서 멱등성 검사를 `Order` 조회로 대체:

```java
// 변경 전
Optional<Long> existingOrderId = orderIdempotencyQueryService.findOrderIdByRequestId(userId, inDto.requestId());

// 변경 후
Optional<Order> existingOrder = orderQueryService.findByUserIdAndRequestId(userId, inDto.requestId());
```

5.  `OrderCommandService.execute()`에서 멱등성 키 저장 삭제 — `Order` 자체에 `requestId`가 포함되므로 별도 저장 불필요.

**개선 효과:**

- ~14개 파일 삭제
- `Order`가 자신의 멱등성을 직접 소유 → 도메인 응집도 향상
- 별도 테이블 JOIN/조회 제거 → 쿼리 단순화

---

### **F: 좋아요 행 (INSERT, 중복 데이터) → UNIQUE constraint**

**현재 구현:**

```java
// check-then-create (race condition 가능)
Optional<ProductLike> existing = productLikeCommandService.findLike(userId, targetId);
if (existing.isPresent()) {
    return ProductLikeOutDto.from(existing.get());
}
ProductLike productLike = productLikeCommandService.createLike(userId, targetId);
```

**의사결정 트리 적용:**

```
Q1. 중복의 원인이 무엇인가?
  → 비즈니스 규칙 위반. "같은 사용자가 같은 상품에 좋아요를 두 번 누를 수 없다."
    → UNIQUE constraint.
```

**개선 내용:**

1.  좋아요 테이블에 `(user_id, target_type, target_id)` 복합 유니크 인덱스 추가.
2.  `findLike()` 사전 조회로 99.9%의 중복 요청을 gracefully 처리 (따닥 등).
3.  DB 유니크 제약은 데이터 무결성의 silent safety net. try-catch로 흐름 제어하지 않음.

```java
// 개선 후 흐름
// 1. 상품 좋아요 생성 (멱등 — 사전 조회로 99.9% 중복 차단, DB 유니크 제약이 데이터 무결성 보장)
@Transactional
public ProductLikeOutDto createLike(String loginId, String password, Long targetId) {

    // 사용자 인증
    Long userId = productLikeCommandService.authenticate(loginId, password);

    // 기존 좋아요 존재 시 기존 반환 (멱등 — 따닥 등 대부분의 중복 요청을 여기서 차단)
    Optional<ProductLike> existing = productLikeCommandService.findLike(userId, targetId);
    if (existing.isPresent()) {
        return ProductLikeOutDto.from(existing.get());
    }

    // 좋아요 생성 (0.01% 레이스 시 DB 유니크 제약이 안전망 역할 — 500 → 클라이언트 재시도 → 사전 조회에서 멱등 반환)
    ProductLike productLike = productLikeCommandService.createLike(userId, targetId);

    // 좋아요 수 증가 (Cross-BC 부수효과)
    productLikeCommandService.increaseLikeCount(targetId);

    return ProductLikeOutDto.from(productLike);
}
```

**왜 try-catch를 쓰지 않는가:** `@Transactional` 내에서 `DataIntegrityViolationException`이 발생하면 Spring이 TX를 rollback-only로 마킹한다. catch해서 다른 값을 반환해도 TX 커밋 시 `UnexpectedRollbackException`이 터진다. 따라서 DB 제약 위반을 흐름 제어 수단으로 쓰는 것은 `@Transactional`과 양립할 수 없다.

사전 조회(`findLike`)가 따닥 등 대부분의 중복 요청을 처리하고, DB 유니크 제약은 극히 드문 레이스에서 **데이터 무결성만 보장하는 안전망**이다.

---

## 정리

결국 핵심은 "기술에서 출발하지 말고, 비즈니스 특성에서 출발하라"는 것이었다.

- 좋아요 수처럼 단순 증감에는 원자적 카운터가 맞고, 재고처럼 복잡한 검증이 필요한 곳에는 비관적 락이 맞다.
- INSERT 중복 방어는 "비즈니스 규칙 위반"과 "네트워크 재전송"을 구분해야 하고, 두 경우 모두 최종 방어선은 DB 제약이다.
- 기술 선택이 비즈니스 특성과 불일치하면, 테스트가 실패하거나(`7/10`), 불필요한 복잡도가 생기거나(14개 파일), 네이밍이 의도와 어긋난다(`idempotencyKey`).

구현 결과:
- **A(좋아요 수 원자적 카운터)**: 완료 — `ProductLikeCountCommandFacade` 삭제, 네이티브 SQL `UPDATE SET like_count = like_count ± 1`
- **B(재고 차감 비관적 락)**: 유지 — 변경 없음
- **C(쿠폰 사용 락 제거)**: 완료 — `@Version`, `@Retryable`, `@Recover` 전체 삭제
- **D(쿠폰 발급 복합 유니크)**: 완료 — `idempotencyKey` → `(user_id, coupon_template_id)` 복합 유니크, `maxIssuePerUser` 필드 삭제
- **E(주문 requestId 통합)**: 완료 — `IdempotencyKey` 관련 14개 파일 삭제, `Order.requestId`로 통합
- **F(좋아요 행 유니크 제약)**: 완료 — `(user_id, target_type, target_id)` 복합 유니크 추가
- 이벤트 기반 비동기(좋아요 수 동기화) 전환은 이후 별도 문서에서 다룸
