# Transaction & Persistence Context 분석 보고서

> 분석 대상: `round4/concurrency_control` 브랜치 전체 코드
> 분석 기준: Spring @Transactional, JPA, QueryDSL 기반 트랜잭션 범위, 영속성 컨텍스트, 쿼리 실행 시점

---

## 1. Transaction Boundary 분석

### 1-1. OrderCommandFacade.createOrder() — 가장 복잡한 유스케이스

```
현재 트랜잭션 범위:
  OrderCommandFacade.createOrder()                    ← @Transactional 시작
  ├─ OrderCheckoutCommandService.authenticate()        (readOnly=true, User BC ACL)
  ├─ OrderIdempotencyQueryService.findOrderIdByRequestId()  (readOnly=true, 조회)
  ├─ [조기 반환] OrderQueryService.findById()          (readOnly=true, 조회)
  ├─ OrderCheckoutCommandService.readCartItemsByIds()  (readOnly=true, Cart BC ACL)
  ├─ OrderCheckoutCommandService.readProducts()        (readOnly=true, Catalog BC ACL)
  ├─ OrderCheckoutCommandService.decreaseStocks()      (@Transactional, 쓰기 — 비관적 락)
  │   └─ OrderStockManager → ProductCommandFacade.decreaseStock()
  │       └─ ProductCommandService.decreaseStock()     (SELECT FOR UPDATE + save)
  ├─ OrderPlacementCommandService.createOrder()        (@Transactional, 쓰기)
  │   ├─ OrderCommandRepository.save()
  │   └─ IdempotencyKeyCommandRepository.save()
  └─ OrderPlacementCommandService.deleteCartItems()    (@Transactional, 쓰기 — Cart BC ACL)
```

**트랜잭션이 필요한 핵심 작업:**
- 재고 차감 (`decreaseStocks`) — 비관적 쓰기 락
- 주문 생성 + 멱등성 키 저장 (`createOrder`)
- 장바구니 정리 (`deleteCartItems`)

**트랜잭션이 불필요한 작업 (현재 포함됨):**
- 사용자 인증 (`authenticate`) — 읽기 전용
- 멱등성 검사 (`findOrderIdByRequestId`) — 읽기 전용
- 장바구니 조회 (`readCartItemsByIds`) — 읽기 전용
- 상품 정보 조회 (`readProducts`) — 읽기 전용

---

### 1-2. ProductLikeCommandFacade.createLike() — Cross-BC 좋아요 수 동기화

```
현재 트랜잭션 범위:
  ProductLikeCommandFacade.createLike()                ← @Transactional 시작
  ├─ ProductLikeCommandService.authenticate()           (readOnly=true, User BC ACL)
  ├─ ProductLikeCommandService.findLike()               (readOnly=true, 조회)
  ├─ [조기 반환 가능]
  ├─ ProductLikeCommandService.createLike()             (@Transactional, 쓰기)
  │   ├─ ProductLikeTargetValidator.validate()          (Catalog BC ACL — 읽기)
  │   └─ ProductLikeCommandRepository.save()
  └─ ProductLikeCommandService.increaseLikeCount()      (@Transactional, 쓰기)
      └─ ProductLikeCountSyncer → ProductLikeCountCommandFacade.increaseLikeCount()
          ├─ ProductQueryService.findActiveById()       (readOnly=true, 조회)
          └─ ProductCommandService.increaseLikeCount()  (@Transactional, 쓰기)
```

**트랜잭션이 필요한 핵심 작업:**
- 좋아요 생성 (`createLike`)
- 좋아요 수 증가 (`increaseLikeCount`)

---

### 1-3. BrandCommandFacade.deleteBrand() — Cross-BC 정리

```
현재 트랜잭션 범위:
  BrandCommandFacade.deleteBrand()                     ← @Transactional 시작
  ├─ BrandQueryService.getBrandById()                   (readOnly=true, 조회)
  ├─ ProductQueryService.existsActiveByBrandId()        (readOnly=true, 조회)
  ├─ BrandCommandService.deleteBrand()                  (@Transactional, soft delete)
  └─ BrandCommandService.deleteAllBrandLikes()          (@Transactional, Cross-BC)
      └─ BrandLikeCleanupManager → BrandLikeCommandFacade.deleteAllByBrandId()
```

---

### 1-4. ProductCommandFacade.deleteProduct() — 다중 Cross-BC 정리

```
현재 트랜잭션 범위:
  ProductCommandFacade.deleteProduct()                 ← @Transactional 시작
  ├─ ProductQueryService.findActiveById()               (readOnly=true, 조회)
  ├─ ProductCommandService.deleteProduct()              (@Transactional, soft delete)
  ├─ ProductCommandService.deleteAllProductLikes()      (@Transactional, Engagement BC ACL)
  └─ ProductCommandService.deleteAllCartItems()         (@Transactional, Cart BC ACL)
```

---

## 2. 불필요하게 큰 트랜잭션 식별

### [발견 1] OrderCommandFacade.createOrder() — 읽기 로직이 쓰기 트랜잭션에 포함

**심각도: 높음**

현재 하나의 `@Transactional` 안에 **인증, 멱등성 검사, 장바구니 조회, 상품 조회** 등 4개의 읽기 전용 작업이 쓰기 트랜잭션에 포함되어 있다.

| 작업 | 유형 | 현재 | 이상적 |
|------|------|------|--------|
| authenticate() | 읽기 (Cross-BC) | 쓰기 트랜잭션 내부 | 트랜잭션 외부 |
| findOrderIdByRequestId() | 읽기 | 쓰기 트랜잭션 내부 | 트랜잭션 외부 |
| readCartItemsByIds() | 읽기 (Cross-BC) | 쓰기 트랜잭션 내부 | 트랜잭션 외부 |
| readProducts() | 읽기 (Cross-BC) | 쓰기 트랜잭션 내부 | 트랜잭션 외부 |
| decreaseStocks() | **쓰기** | 쓰기 트랜잭션 내부 | 쓰기 트랜잭션 시작 |
| createOrder() | **쓰기** | 쓰기 트랜잭션 내부 | 쓰기 트랜잭션 유지 |
| deleteCartItems() | **쓰기** | 쓰기 트랜잭션 내부 | 쓰기 트랜잭션 유지 |

**문제점:**
- 읽기 작업 동안 DB 커넥션을 쓰기 트랜잭션 모드로 점유
- Cross-BC ACL 호출(User, Cart, Catalog)이 모두 외부 Facade 호출인데 트랜잭션 내부에서 수행
- 비관적 락(`SELECT FOR UPDATE`)이 이른 시점에 획득되면, 읽기 작업이 길어질수록 락 유지 시간이 증가 → 다만 현재 코드에서는 `decreaseStocks()`가 읽기 이후에 호출되므로 이 특정 문제는 회피됨

---

### [발견 2] ProductLikeCommandFacade.createLike() — Cross-BC 쓰기가 동일 트랜잭션

**심각도: 중간**

좋아요 생성(Engagement BC)과 좋아요 수 증가(Catalog BC)가 동일 트랜잭션에 묶여 있다. `increaseLikeCount()`가 ACL을 경유하여 `ProductLikeCountCommandFacade`를 호출하는데, 이 Facade의 `@Transactional`은 Facade 호출 시점에 이미 상위 트랜잭션이 존재하므로 **REQUIRED 전파에 의해 동일 트랜잭션에 참여**한다.

**의도된 설계일 가능성:**
- 좋아요 생성과 카운트 증가의 **원자성 보장**이 목적이라면 현재 구조가 적절
- 다만 카운트가 정확하지 않아도 되는 eventual consistency로 전환한다면, 이벤트 기반 비동기 처리로 분리 가능

---

### [발견 3] ProductLikeCountCommandFacade — likeCount 동시성 제어 부재

**심각도: 높음 (branch명 `round4/concurrency_control` 관점)**

```java
// ProductLikeCountCommandFacade.increaseLikeCount()
Product product = productQueryService.findActiveById(productId);  // 일반 조회 (락 없음)
productCommandService.increaseLikeCount(product);                  // save()
```

`findActiveById()`는 **비관적 락 없이** 상품을 조회하고, 이후 `product.increaseLikeCount()` → `save()` 호출한다. 동시에 두 사용자가 좋아요를 누르면:

```
Thread A: read likeCount=10 → set 11 → save
Thread B: read likeCount=10 → set 11 → save   ← Lost Update!
```

반면 `decreaseStock()`은 `findActiveByIdForUpdate()` (SELECT FOR UPDATE)를 사용하여 올바르게 동시성 제어를 하고 있다.

| 메서드 | 조회 방식 | 동시성 제어 |
|--------|-----------|------------|
| `decreaseStock()` | `findActiveByIdForUpdate()` | 비관적 쓰기 락 (정상) |
| `increaseLikeCount()` | `findActiveById()` | **없음 (Lost Update 가능)** |
| `decreaseLikeCount()` | `findActiveById()` | **없음 (Lost Update 가능)** |

---

### [발견 4] ProductCommandFacade.deleteProduct() — 다중 Cross-BC 정리가 단일 트랜잭션

**심각도: 낮음**

상품 삭제 시 좋아요 정리(Engagement BC)와 장바구니 정리(Cart BC)가 단일 트랜잭션에 포함된다. 정리 작업이 실패하면 상품 삭제 자체가 롤백된다.

**의도된 설계일 가능성:**
- 데이터 정합성 관점에서 삭제된 상품에 대한 좋아요/장바구니가 남아있으면 안 되므로, 원자성 보장은 합리적
- 다만 정리 작업이 대량일 경우 트랜잭션이 길어질 수 있음

---

## 3. JPA / 영속성 컨텍스트 관점 분석

### 3-1. Entity → Domain 변환 패턴의 영속성 컨텍스트 분리

현재 아키텍처는 **RepositoryImpl에서 Entity를 Domain으로 변환 후 반환**한다. 이 패턴 덕분에:

- Service/Facade에서 다루는 객체는 **영속성 컨텍스트에 등록되지 않은 순수 도메인 객체**
- 변경 감지(dirty checking)가 발생하지 않음
- 지연 로딩 문제가 원천 차단됨 (`LazyInitializationException` 불가)

```
Entity (영속) → mapper.toDomain() → Domain (비영속) → Service/Facade 사용
Domain (비영속) → mapper.toEntity() → Entity (비영속) → jpaRepository.save() → 영속화
```

**결론:** 이 아키텍처에서 전통적인 JPA dirty checking / lazy loading 문제는 발생하지 않는다.

### 3-2. `save()` 시 flush 타이밍

현재 코드에서 `productCommandRepository.save(product)`는 내부적으로:
1. `mapper.toEntity(domain)` — **새 Entity 인스턴스 생성** (id 포함)
2. `jpaRepository.save(entity)` — JPA `merge()` 수행 (id가 있으므로)
3. `mapper.toDomain(savedEntity)` — 영속 Entity를 다시 Domain으로 변환

**핵심:** 매번 새 Entity를 만들어 `save()`하므로, JPA의 변경 감지 대신 **명시적 merge 전략**을 사용한다. 이는 영속성 컨텍스트의 1차 캐시와 충돌할 수 있으나, 트랜잭션 범위가 짧으므로 실질적 문제 없음.

### 3-3. OrderCommandRepositoryImpl — 복합 저장의 flush 순서

```java
// 1. Order 저장
OrderEntity savedOrderEntity = orderJpaRepository.save(orderEntity);
// 2. OrderItem 저장 (savedOrderEntity.getId() 사용)
List<OrderItemEntity> orderItemEntities = orderItemMapper.toEntities(items, savedOrderEntity.getId());
orderItemJpaRepository.saveAll(orderItemEntities);
```

- `@GeneratedValue(IDENTITY)` 전략이므로 `save()` 시점에 즉시 INSERT + ID 할당
- OrderItem 저장 시 OrderEntity의 ID가 이미 확보되어 있으므로 순서 문제 없음

### 3-4. 조회 전용 메서드의 `readOnly = true` 적용 현황

| 클래스 | readOnly 적용 | 비고 |
|--------|:---:|------|
| 모든 QueryService 메서드 | O | 정상 |
| 모든 QueryFacade 메서드 | O | 정상 |
| Service.authenticate() 메서드들 | O | 정상 |
| OrderCheckoutCommandService.readCartItemsByIds() | O | 정상 |
| OrderCheckoutCommandService.readProducts() | O | 정상 |

**결론:** 읽기 전용 메서드에 `readOnly = true`가 일관적으로 적용되어 있다.

### 3-5. 비관적 락 사용 시 영속성 컨텍스트

`findActiveByIdForUpdate()`는 JPA의 `@Lock(PESSIMISTIC_WRITE)`로 `SELECT ... FOR UPDATE`를 실행한다. 반환된 Entity는 영속 상태이지만, `mapper.toDomain()`으로 비영속 도메인 객체로 변환 후 Service에 전달된다. 이후 도메인에서 stock 변경 → `mapper.toEntity()` → **새 Entity 인스턴스로 `save()`** 호출.

**잠재 이슈:** 비관적 락으로 가져온 Entity와 `save()`에서 merge하는 Entity가 **다른 인스턴스**다. 영속성 컨텍스트에 동일 ID의 Entity가 두 개 존재하게 되는데, JPA `merge()`는 기존 영속 Entity에 상태를 복사하므로 정상 동작한다. 다만 이는 JPA의 내부 동작에 의존하는 부분이므로 인지해 둘 필요가 있다.

---

## 4. 개선 제안 (선택적)

### [개선안 1] OrderCommandFacade.createOrder() — 트랜잭션 분리

```
[Before] 단일 @Transactional
  인증 → 멱등성 검사 → 장바구니 조회 → 상품 조회 → 재고 차감 → 주문 생성 → 장바구니 정리

[After] Facade에서 orchestration, 쓰기만 트랜잭션
  인증 (트랜잭션 없음 or readOnly)
  멱등성 검사 (readOnly)
  장바구니 조회 (readOnly)
  상품 조회 (readOnly)
  ─── @Transactional 시작 ───
  재고 차감 → 주문 생성 → 멱등성 키 저장 → 장바구니 정리
  ─── @Transactional 종료 ───
```

**고려 사항:**
- Facade에서 `@Transactional`을 제거하고, 쓰기 작업만 묶는 별도 Service 메서드를 호출하는 방식
- 읽기 후 쓰기 사이에 데이터가 변경될 수 있음 (TOCTOU) → 하지만 `decreaseStock()`이 비관적 락으로 보호하므로 재고에 대해서는 안전
- 장바구니 항목이 읽기 이후 삭제될 가능성 → 주문 생성 시 검증 로직으로 보완 필요

---

### [개선안 2] ProductLikeCount — 동시성 제어 추가

**Option A: 비관적 락 적용** (decreaseStock과 동일 패턴)
```java
// ProductLikeCountCommandFacade.increaseLikeCount()
Product product = productQueryService.findActiveByIdForUpdate(productId);  // SELECT FOR UPDATE
productCommandService.increaseLikeCount(product);
```

**Option B: UPDATE 쿼리 직접 실행** (더 효율적)
```sql
UPDATE product SET like_count = like_count + 1 WHERE id = ? AND deleted_at IS NULL
```
- Entity를 조회하지 않으므로 영속성 컨텍스트 오버헤드 없음
- 원자적 연산이므로 Lost Update 원천 차단

**Option C: 이벤트 기반 비동기 처리**
- 좋아요 수는 정확성보다 가용성이 중요할 수 있음
- `@TransactionalEventListener`로 좋아요 생성 이벤트 발행 → 비동기 카운트 증가

**Trade-off:**

| Option | 정합성 | 성능 | 복잡도 |
|--------|:---:|:---:|:---:|
| A. 비관적 락 | 강 | 낮음 (락 대기) | 낮음 |
| B. UPDATE 직접 | 강 | 높음 | 중간 |
| C. 비동기 이벤트 | 최종적 일관성 | 높음 | 높음 |

---

### [개선안 3] ProductLikeCommandFacade.createLike() — Cross-BC 부수효과 분리

```
[Before]
  @Transactional
  좋아요 생성 + 카운트 증가 (동일 트랜잭션)

[After - 이벤트 기반]
  @Transactional
  좋아요 생성
  → ProductLikeCreatedEvent 발행

  @TransactionalEventListener(phase = AFTER_COMMIT)
  → 카운트 증가 (별도 트랜잭션)
```

**고려 사항:**
- 좋아요 생성은 성공했지만 카운트 증가가 실패한 경우 → 비정합 허용 가능?
- 주기적 배치로 카운트 보정 필요 여부

---

### [개선안 4] deleteProduct / deleteBrand — 부수효과 비동기화 (선택적)

현재 구조에서는 상품/브랜드 삭제 시 좋아요, 장바구니 정리를 동기로 수행한다. 정리 대상이 수만 건이면 트랜잭션이 길어질 수 있다.

```
[After - 이벤트 기반]
  @Transactional: 상품 soft delete만 수행
  → ProductDeletedEvent 발행

  @TransactionalEventListener: 좋아요/장바구니 정리 (별도 트랜잭션)
```

**현재 구조가 적절한 경우:** 정리 대상이 소량이고, 강한 일관성이 필요한 경우

---

## 5. 요약

| 항목 | 현황 | 판정 |
|------|------|------|
| Controller `@Transactional` 사용 | 없음 | 정상 |
| 클래스 레벨 `@Transactional` | 없음 | 정상 |
| 메서드 레벨 `@Transactional` | 모든 Facade/Service | 정상 |
| `readOnly = true` 적용 | 모든 조회 메서드 | 정상 |
| Entity → Domain 분리 | RepositoryImpl에서 변환 | dirty checking/lazy loading 차단됨 |
| 재고 차감 동시성 | 비관적 락 (`FOR UPDATE`) + productId 정렬 | 정상 |
| **likeCount 동시성** | **락 없음** | **Lost Update 가능 — 개선 필요** |
| **OrderCommandFacade 트랜잭션 범위** | **읽기 4건 + 쓰기 3건 단일 트랜잭션** | **트랜잭션이 불필요하게 큼 — 개선 고려** |
| Cross-BC 부수효과 | 동기 처리 (동일 트랜잭션) | 설계 의도에 따라 적절할 수 있음 |
