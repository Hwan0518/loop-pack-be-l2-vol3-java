# Transaction / Query 분석 결과

> 분석 대상: `commerce-api` 전체 Facade / Service / Repository 레이어
> 분석 기준: 트랜잭션 범위, 영속성 컨텍스트, 쿼리 실행 시점

---

## 1. Transaction Boundary 분석

### 1-1. 주문 생성 (`OrderCommandFacade.createOrder`)

```
현재 트랜잭션 범위:

OrderCommandFacade.createOrder() — NO TX (Facade 자체에 @Transactional 없음)
├─ [Phase 1: 읽기] 각 Service가 자체 readOnly TX 보유
│   ├─ 사용자 인증         (OrderCheckoutCommandService — readOnly TX)
│   ├─ 멱등성 검사          (OrderQueryService — readOnly TX)
│   ├─ 장바구니 항목 조회    (OrderCheckoutCommandService — readOnly TX)
│   └─ 상품 정보 조회       (OrderCheckoutCommandService — readOnly TX)
│
└─ [Phase 2: 쓰기] OrderCommandService.execute() — 단일 @Transactional
    ├─ 재고 차감 (N건) — ACL → ProductCommandFacade → ProductCommandService.decreaseStock()
    │   └─ PESSIMISTIC_WRITE 락 (productId 오름차순 — 데드락 방지)
    ├─ 쿠폰 적용 — ACL → IssuedCouponCommandFacade.applyToCoupon() (TX 없음)
    │   └─ IssuedCouponCommandService.applyToCoupon() — REQUIRED → 외부 TX에 참여
    ├─ 주문 생성 + 저장
    └─ 장바구니 정리 — ACL → CartItemCommandFacade — REQUIRED → 외부 TX에 참여
```

- **트랜잭션이 필요한 핵심 작업**: 재고 차감, 쿠폰 상태 변경, 주문 생성, 장바구니 삭제
- **의도**: Phase 1(읽기)과 Phase 2(쓰기)를 명시적으로 분리하여 트랜잭션 범위를 최소화한 설계. **잘 설계된 패턴.**

---

### 1-2. 상품 좋아요 생성 (`ProductLikeCommandFacade.createLike`)

```
현재 트랜잭션 범위:

ProductLikeCommandFacade.createLike() — @Transactional (하나의 TX에 모든 작업 포함)
├─ 사용자 인증                (ProductLikeCommandService — readOnly TX → 외부 TX에 참여)
│   └─ ACL → UserQueryFacade — readOnly TX → 외부 TX에 참여
├─ 기존 좋아요 조회            (ProductLikeCommandService — readOnly TX → 외부 TX에 참여)
├─ 좋아요 생성                (ProductLikeCommandService — TX → 외부 TX에 참여)
│   └─ 좋아요 대상 검증 — ACL → ProductQueryFacade — readOnly TX → 외부 TX에 참여
│   └─ ProductLike save
└─ 좋아요 수 증가 (Cross-BC)  (ProductLikeCommandService — TX → 외부 TX에 참여)
    └─ ACL → ProductCommandFacade → ProductCommandService.increaseLikeCount()
        └─ 원자적 카운터 (native SQL UPDATE)
```

- **트랜잭션이 필요한 핵심 작업**: 좋아요 생성, 좋아요 수 증가

---

### 1-3. 상품 삭제 (`ProductCommandFacade.deleteProduct`)

```
현재 트랜잭션 범위:

ProductCommandFacade.deleteProduct() — @Transactional (하나의 TX에 3개 BC 포함)
├─ 활성 상품 조회              (ProductQueryService — readOnly TX → 외부 TX에 참여)
├─ 상품 삭제 (soft delete)     (ProductCommandService — TX → 외부 TX에 참여)
├─ 상품 좋아요 전체 삭제        (ProductCommandService → ACL → ProductLikeCommandFacade — TX 참여)
│   └─ Engagement BC 의 likes 테이블 DELETE
└─ 장바구니 항목 전체 삭제       (ProductCommandService → ACL → CartItemCommandFacade — TX 참여)
    └─ Cart BC 의 cart_items 테이블 DELETE
```

- **트랜잭션이 필요한 핵심 작업**: 상품 삭제, 좋아요/장바구니 정리

---

### 1-4. 쿠폰 발급 (`IssuedCouponCommandFacade.issueCoupon`)

```
현재 트랜잭션 범위:

IssuedCouponCommandFacade.issueCoupon() — @Transactional
├─ 사용자 인증                 (IssuedCouponCommandService — readOnly TX → 외부 TX에 참여)
├─ 로컬 캐시 중복 검사         (IssuedCouponCommandService — TX 없음 ✓)
├─ 쿠폰 템플릿 검증            (CouponTemplateQueryService — readOnly TX → 외부 TX에 참여)
└─ 발급 쿠폰 저장              (IssuedCouponCommandService — TX → 외부 TX에 참여)
```

- **트랜잭션이 필요한 핵심 작업**: 발급 쿠폰 저장

---

## 2. 불필요하게 큰 트랜잭션 식별

### [문제 1] Cross-BC 호출이 트랜잭션 내부에 포함 — Engagement ↔ Catalog

**대상**: `ProductLikeCommandFacade.createLike()`, `deleteLike()`

Facade에 `@Transactional`이 걸려 있어, Service 메서드들의 `REQUIRED` 전파 규칙에 의해 **Engagement BC + Catalog BC 작업이 하나의 TX에 묶인다.**

```
[현재]
ProductLikeCommandFacade @Transactional — 단일 TX
├─ 사용자 인증 (User BC → readOnly인데 write TX에 참여)
├─ 좋아요 조회 (Engagement BC → readOnly인데 write TX에 참여)
├─ 좋아요 생성 (Engagement BC — write)
└─ 좋아요 수 증가 (Catalog BC — write, 원자적 카운터)  ← Cross-BC가 같은 TX
```

**영향**:
- Engagement BC와 Catalog BC가 트랜잭션 커플링 (원자성이 보장되지만 격리 관점에서 BC 경계를 무시)
- 원자적 카운터(`native SQL UPDATE`)는 본래 독립적으로 실행 가능한데, 좋아요 생성과 같은 TX에 묶여 있어 TX가 길어짐
- 현재 구조에서 좋아요 수는 **정확 동기** 의도이므로 trade-off를 인지한 설계로 보임

---

### [문제 2] 읽기 전용 로직이 쓰기 트랜잭션에 포함

**대상**: 대부분의 Command Facade

| Facade | readOnly 작업 | 포함된 TX |
|--------|--------------|----------|
| `ProductLikeCommandFacade.createLike` | 인증, 기존 좋아요 조회, 좋아요 대상 검증 | write TX |
| `BrandLikeCommandFacade.createLike` | 인증, 기존 좋아요 조회, 브랜드 검증 | write TX |
| `CartItemCommandFacade.addItem` | 인증 | write TX |
| `IssuedCouponCommandFacade.issueCoupon` | 인증, 쿠폰 템플릿 검증 | write TX |
| `ProductCommandFacade.createProduct` | 브랜드 존재 확인 | write TX |
| `ProductCommandFacade.updateProduct` | 활성 상품 조회, 브랜드 조회 | write TX |

**원인**: Service 메서드에 `@Transactional(readOnly = true)`가 있지만, Facade의 `@Transactional`(쓰기)이 먼저 열린 상태에서 `REQUIRED` 전파로 외부 TX에 참여하므로 **readOnly 힌트가 무시됨.**

**영향**:
- DB 커넥션이 readOnly 최적화 (DBMS 쿼리 캐시, 복제 슬레이브 라우팅 등)를 받지 못함
- 쓰기가 불필요한 구간에서도 변경 감지(dirty checking)가 활성화

---

### [문제 3] OrderCommandService.execute() 내 비관적 락 장기 보유

**대상**: `OrderCommandService.execute()`

```
OrderCommandService.execute() @Transactional
├─ 재고 차감 (N건) — PESSIMISTIC_WRITE 락 획득 ← 여기서 락 시작
│   (productId 오름차순 정렬로 데드락 방지 — 잘 설계됨)
├─ 쿠폰 적용 — DB 조회 + 상태 변경    ← 락이 계속 유지 중
├─ 주문 생성 + 저장                  ← 락이 계속 유지 중
└─ 장바구니 정리 — DELETE 실행        ← 락이 계속 유지 중
                                     ← TX 커밋 시점에 락 해제
```

**영향**:
- 재고 비관적 락이 쿠폰 처리 + 주문 저장 + 장바구니 정리까지 유지됨
- 동일 상품에 대한 동시 주문이 직렬화되는 구간이 필요 이상으로 길어짐
- 다만 Phase 1(읽기)을 Facade에서 TX 밖으로 분리한 것은 이미 개선이 적용된 상태

---

### [문제 4] 상품 삭제 시 3개 BC가 하나의 TX에 포함

**대상**: `ProductCommandFacade.deleteProduct()`

- Catalog BC (상품 soft delete) + Engagement BC (좋아요 전체 삭제) + Cart BC (장바구니 전체 삭제)
- 좋아요/장바구니 정리는 부수효과이므로 이벤트 기반 비동기 처리가 가능한 후보

---

## 3. JPA / 영속성 컨텍스트 관점 분석

### [양호] QueryDSL DTO Projection

`ProductQuerydslRepository`의 `searchProducts()`, `searchAdminProducts()`는 `Projections.constructor()`로 **DTO 직접 조회**를 사용. Entity를 거치지 않아 영속성 컨텍스트에 올라가지 않으며, 변경 감지 비용 없음.

### [양호] 원자적 카운터와 영속성 컨텍스트 격리

`ProductJpaRepository.increaseLikeCount()` / `decreaseLikeCount()`는 **native SQL + @Modifying**으로 실행. 좋아요 수 변경 시점에 같은 Product Entity가 영속성 컨텍스트에 있을 가능성은 현재 흐름에서 거의 없음.

- `ProductLikeCommandFacade.createLike()` 흐름에서 Product Entity를 직접 조회하지 않음 (좋아요 대상 검증은 존재 여부만 확인)
- 따라서 native SQL UPDATE와 영속성 컨텍스트 불일치 이슈 없음

### [주의] ProductCommandService.decreaseStock()의 merge 패턴

```java
// 1. 비관적 쓰기 락으로 Entity 조회 → 도메인 모델 변환 (Entity는 영속성 컨텍스트에 남음)
Product product = productQueryRepository.findActiveByIdForUpdate(productId);

// 2. 도메인 로직 실행 (Domain 객체의 stock 변경)
product.decreaseStock(quantity);

// 3. 도메인 → 새 Entity 생성 → save() → JPA merge
productCommandRepository.save(product);
```

- Step 1에서 조회된 Entity A가 영속성 컨텍스트에 남아 있음 (FOR UPDATE 락 포함)
- Step 3에서 `mapper.toEntity(product)`로 새 Entity B를 생성하여 `save()` → `merge(B)`
- `merge()`는 같은 ID의 관리 엔티티 A를 찾아 B의 상태를 복사 → A에 새 stock 값이 반영
- version이 domain에서 보존되므로 **정상 동작하지만**, Entity를 두 번 만드는 비효율이 존재
- 현재 구조에서는 Domain Model 중심 패턴의 의도적 trade-off로 보임

### [양호] @Transactional(readOnly = true) 적용 현황

- 모든 Query Facade / Query Service에 `readOnly = true` 적용됨
- 단, 위 [문제 2]에서 언급했듯이 Command Facade TX 내에서 호출될 때는 readOnly 힌트가 무시됨

### [양호] Entity 지연 로딩 이슈 없음

- 모든 Entity에 연관관계 매핑(`@ManyToOne`, `@OneToMany` 등)이 없음
- `brandId`, `productId` 등 FK를 필드로 직접 관리 → 지연 로딩으로 인한 추가 쿼리 발생 위험 없음

---

## 4. Improvement Proposal (선택적 제안)

### [개선안 1] 좋아요 수 동기화를 최종적 일관성으로 전환

**대상**: `ProductLikeCommandFacade.createLike()` / `deleteLike()`

```
[현재] — 강한 일관성 (동기, 같은 TX)
ProductLikeCommandFacade @Transactional
├─ 좋아요 생성 (Engagement BC)
└─ 좋아요 수 증가 (Catalog BC — 원자적 카운터)   ← 같은 TX

[개선안] — 최종적 일관성 (이벤트)
ProductLikeCommandFacade @Transactional
└─ 좋아요 생성 (Engagement BC)
    └─ @TransactionalEventListener → ProductLikeCountSyncer.increaseLikeCount()
```

**장점**:
- TX 범위 축소 (Engagement BC만)
- BC 간 트랜잭션 디커플링

**고려 사항**:
- 좋아요 수가 실시간이 아닌 최종적 일관성으로 전환됨
- 원자적 카운터 자체는 멱등하지 않으므로 이벤트 재처리 시 중복 증가 방지 필요
- 현재 원자적 카운터 방식이 이미 동시성을 잘 처리하므로, 비동기로 바꿀 경우 복잡도 대비 실익을 따져봐야 함

---

### [개선안 2] 주문 생성 시 비관적 락 범위 최소화

**대상**: `OrderCommandService.execute()`

```
[현재]
OrderCommandService.execute() @Transactional  ← 큰 TX 하나
├─ 재고 차감 (비관적 락 획득)
├─ 쿠폰 적용
├─ 주문 생성
└─ 장바구니 정리

[개선안] — 장바구니 정리를 TX 밖으로 분리
OrderCommandService.execute() @Transactional
├─ 재고 차감 (비관적 락 획득)
├─ 쿠폰 적용
└─ 주문 생성
                                              ← TX 커밋 (락 해제)
OrderCommandFacade (TX 외부)
└─ 장바구니 정리 (별도 TX)                       ← TX 밖에서 실행
```

**장점**:
- 비관적 락 유지 시간 단축
- 장바구니 정리 실패가 주문을 롤백하지 않음

**고려 사항**:
- 장바구니 정리 실패 시 보상 로직 필요 (주문은 성공했지만 장바구니에 항목이 남아있음)
- 현재 구조에서는 장바구니 정리가 빠르게 끝나므로 실질적 성능 이슈는 적을 수 있음

---

### [개선안 3] 상품 삭제 시 부수효과를 이벤트로 분리

**대상**: `ProductCommandFacade.deleteProduct()`

```
[현재]
ProductCommandFacade.deleteProduct() @Transactional
├─ 상품 삭제 (Catalog BC)
├─ 좋아요 전체 삭제 (Engagement BC)   ← Cross-BC, 같은 TX
└─ 장바구니 전체 삭제 (Cart BC)       ← Cross-BC, 같은 TX

[개선안]
ProductCommandFacade.deleteProduct() @Transactional
└─ 상품 삭제 (Catalog BC)
    └─ ProductDeletedEvent 발행
        ├─ @TransactionalEventListener → 좋아요 전체 삭제
        └─ @TransactionalEventListener → 장바구니 전체 삭제
```

**장점**:
- 3개 BC 동시 TX → 단일 BC TX로 축소
- 부수효과 실패가 상품 삭제를 롤백하지 않음

**고려 사항**:
- 삭제된 상품의 좋아요/장바구니가 일시적으로 남아있을 수 있음
- 이미 soft delete이므로 실질적 영향은 적음

---

### [개선안 4] Command Facade의 readOnly 작업 TX 분리

**대상**: `ProductLikeCommandFacade.createLike()` 등

```
[현재]
ProductLikeCommandFacade.createLike() @Transactional  ← 전체가 write TX
├─ 인증 (readOnly)        ← readOnly 힌트 무시됨
├─ 기존 좋아요 조회 (readOnly) ← readOnly 힌트 무시됨
├─ 좋아요 생성 (write)
└─ 좋아요 수 증가 (write)

[개선안] — OrderCommandFacade 패턴 적용
ProductLikeCommandFacade.createLike()  ← NO TX (Facade 자체)
├─ 인증 (Service — readOnly TX)
├─ 기존 좋아요 조회 (Service — readOnly TX)
├─ 쓰기 Service에 위임 (Service — write TX)
│   ├─ 좋아요 생성
│   └─ 좋아요 수 증가
```

**장점**:
- readOnly 구간에서 실제 readOnly 최적화 적용 가능
- 쓰기 TX 범위 최소화

**고려 사항**:
- OrderCommandFacade에서 이미 이 패턴이 적용되어 있으므로 일관성 있게 다른 Facade에도 적용 가능
- 단, 좋아요 생성 + 카운트 증가의 원자성이 중요하다면 두 작업은 같은 TX에 유지해야 함

---

## 5. 요약 테이블

| # | 항목 | 심각도 | 상태 |
|---|------|--------|------|
| 1 | OrderCommandFacade 읽기/쓰기 TX 분리 | - | **이미 적용됨 ✓** |
| 2 | Entity 연관관계 없음 (지연 로딩 이슈 없음) | - | **양호 ✓** |
| 3 | QueryDSL DTO Projection 사용 | - | **양호 ✓** |
| 4 | 원자적 카운터 영속성 컨텍스트 격리 | - | **양호 ✓** |
| 5 | Cross-BC 작업이 하나의 TX에 포함 (좋아요, 상품 삭제) | 중 | 의도적 trade-off 가능 |
| 6 | readOnly 작업이 write TX에서 실행 | 낮음 | 성능 최적화 여지 |
| 7 | 주문 시 비관적 락 장기 보유 | 중 | 장바구니 정리 분리 가능 |
| 8 | merge 패턴의 Entity 이중 생성 | 낮음 | Domain Model 패턴의 trade-off |

> 개선안 판정 결과는 `docs/design/05-concurrency-strategy.md` Section 3에 기록됨.
