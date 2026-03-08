> **⚠️ 히스토리 문서**: 이 문서는 낙관적 락 적용 직후의 체크포인트 검토 결과입니다. 여기서 발견된 좋아요 동시성 테스트 실패(10→7)가 `06-optimistic-vs-pessimistic-vs-atomic-counter.md`에서의 전략 재설계 계기가 되었습니다.

# 05. 주문/동시성 체크리스트 검토 결과

## 1) 문서 목적
- 현재 워킹디렉토리 변경사항을 기준으로 주문/동시성 체크리스트 충족 여부를 점검한다.
- 추가 기준(클라이언트 관점의 락 전략, `analyze-query` 스킬 기준)까지 포함해 판정한다.

## 2) 검토 기준

### 기본 체크리스트
- 주문 전체 흐름 원자성 보장
- 쿠폰 유효성/존재성 실패 시 주문 실패
- 재고 부족/미존재 시 주문 실패
- 쿠폰/재고/포인트 중 하나 실패 시 전체 롤백
- 주문 성공 시 모든 처리 정상 반영
- 좋아요/싫어요 동시 요청 시 카운트 정상 반영
- 동일 쿠폰 동시 주문 시 단 1회 사용
- 동일 상품 동시 주문 시 재고 정상 차감

### 추가 기준 A: 클라이언트 기준 동시성 제어
- 사용자가 빠른 결과를 기대하는 경우: 낙관적 락 우선
- 사용자가 기다려도 되는 경우: 비관적 락 고려 (단, 무조건 적용 금지)

### 추가 기준 B: `analyze-query` 스킬 기준
- 불필요하게 큰 트랜잭션이 없어야 함
- 지연로딩/flush/변경감지로 인한 트랜잭션 후반 쿼리 위험이 없어야 함
- `@Transactional(readOnly = true)` 미적용 구간이 없어야 함
- 단순 조회 후 Entity 변경 가능성이 없어야 함
- DTO Projection 대신 Entity 조회 사용이 없어야 함
- QueryDSL 조회 결과의 영속성 컨텍스트 이슈가 없어야 함

> 참고: 사용자가 확인한 내용에 따라 "DB 스키마 자동 반영 이슈"는 본 문서의 주요 리스크에서 제외함.

## 3) 검토 범위
- 주문 생성 유즈케이스:
  - `apps/commerce-api/src/main/java/com/loopers/ordering/order/application/facade/OrderCommandFacade.java`
  - `apps/commerce-api/src/main/java/com/loopers/ordering/order/application/service/OrderCheckoutCommandService.java`
  - `apps/commerce-api/src/main/java/com/loopers/ordering/order/application/service/OrderPlacementCommandService.java`
- 재고 차감 경로:
  - `apps/commerce-api/src/main/java/com/loopers/ordering/order/infrastructure/acl/catalog/OrderStockManagerImpl.java`
  - `apps/commerce-api/src/main/java/com/loopers/catalog/product/application/service/ProductCommandService.java`
  - `apps/commerce-api/src/main/java/com/loopers/catalog/product/infrastructure/jpa/ProductJpaRepository.java`
- 좋아요 동시성 경로:
  - `apps/commerce-api/src/main/java/com/loopers/catalog/product/application/facade/ProductLikeCountCommandFacade.java`
  - `apps/commerce-api/src/main/java/com/loopers/catalog/product/application/service/ProductCommandService.java`
  - `apps/commerce-api/src/main/java/com/loopers/catalog/product/infrastructure/entity/ProductEntity.java`

## 4) 실행 검증 결과

### 통과
- `:apps:commerce-api:test --tests "com.loopers.catalog.product.application.facade.ProductLikeCountCommandFacadeTest"`
- `:apps:commerce-api:test --tests "com.loopers.catalog.product.application.service.ProductCommandServiceTest"`
- `:apps:commerce-api:test --tests "com.loopers.support.common.error.ErrorTypeTest"`
- `:apps:commerce-api:test --tests "com.loopers.support.common.error.GlobalExceptionHandlerTest"`
- `:apps:commerce-api:test --tests "com.loopers.ordering.order.application.service.OrderCheckoutCommandServiceTest"`
- `:apps:commerce-api:test --tests "com.loopers.ordering.order.application.facade.OrderCommandFacadeTest"`

### 실패
- `:apps:commerce-api:test --tests "com.loopers.catalog.product.application.facade.ProductLikeCountConcurrencyTest"`
  - 결과: `expected: 10L but was: 7L`
  - 리포트: `apps/commerce-api/build/test-results/test/TEST-com.loopers.catalog.product.application.facade.ProductLikeCountConcurrencyTest.xml`

## 5) 주요 Findings (심각도 순)

### [High] 좋아요 동시성 요구사항 미충족
- `@Retryable(maxAttempts = 3)` 기반 낙관적 락 재시도로는 고경합 구간에서 요청 일부가 최종 실패한다.
- 실제 통합 테스트에서 10건 요청 대비 likeCount가 7로 검증 실패했다.
- 관련 코드:
  - `ProductLikeCountCommandFacade.increaseLikeCount()`
  - `ProductLikeCountCommandFacade.recoverIncreaseLikeCountConflict()`
  - `ProductLikeCountConcurrencyTest.concurrentIncreaseLikeCount()`

### [High] 주문 생성 트랜잭션이 스킬 기준에서 과대 범위
- `OrderCommandFacade.createOrder()` 단일 트랜잭션 안에 인증/멱등 조회/장바구니 조회/상품 조회/재고 차감/주문 생성/장바구니 정리까지 포함된다.
- 조회/쓰기가 섞여 있고 트랜잭션 유지 시간이 길어질 여지가 있다.
- 관련 코드:
  - `OrderCommandFacade.createOrder()`
  - `OrderCheckoutCommandService.readCartItemsByIds()`
  - `OrderCheckoutCommandService.readProducts()`
  - `OrderCheckoutCommandService.decreaseStocks()`
  - `OrderPlacementCommandService.createOrder()`
  - `OrderPlacementCommandService.deleteCartItems()`

### [High] 쿠폰/포인트 체크리스트 항목은 구현/검증 근거 없음
- 주문 흐름에 쿠폰/포인트 처리 단계가 존재하지 않는다.
- "쿠폰 실패 시 주문 실패", "쿠폰 단건 사용 보장", "포인트 실패 시 전체 롤백" 항목을 증명할 코드/테스트가 없다.

### [Medium] QueryDSL에서 DTO Projection 대신 Entity 직접 조회
- `ProductQuerydslRepository`에서 `select(product, brand.name)` 후 `tuple.get(product)`로 Entity를 꺼내 DTO를 생성한다.
- 스킬 기준 관점에서 "영속성 컨텍스트 포함/변경감지 위험" 후보로 분류된다.
- 관련 코드:
  - `ProductQuerydslRepository.fetchProductsWithBrand()`
  - `ProductQuerydslRepository.searchProducts()`
  - `ProductQuerydslRepository.searchAdminProducts()`

### [Medium] `readOnly` 선언은 많지만 주문 쓰기 유즈케이스에서는 실효 분리가 부족
- 조회 메서드들이 `@Transactional(readOnly = true)`를 사용하더라도, 상위 `@Transactional` 쓰기 트랜잭션에 합류해 실제로는 조회/쓰기 혼합 실행이 된다.
- 관련 코드:
  - `OrderCommandFacade.createOrder()`
  - `OrderCheckoutCommandService.readCartItemsByIds()`
  - `OrderCheckoutCommandService.readProducts()`

### [Medium] 주문 E2E는 실제 재고 동시성/락을 검증하지 않음
- `OrderPortTestConfig`에서 `OrderStockManager`를 테스트 대체 빈(no-op)으로 주입한다.
- E2E가 주문 API 경로는 검증하지만 실제 재고 락/충돌 시나리오는 검증하지 못한다.

## 6) 체크리스트 판정

| 항목 | 판정 | 근거 |
|---|---|---|
| 주문 전체 흐름 원자성 보장 | 부분 충족 | 단일 트랜잭션 구조 존재. 단, 범위가 과대하고 쿠폰/포인트 부재 |
| 쿠폰 유효성/존재성 실패 시 주문 실패 | 미충족 | 쿠폰 처리 단계/테스트 없음 |
| 재고 부족/미존재 시 주문 실패 | 충족 | `decreaseStock` + 비관적락 + 예외 흐름 존재 |
| 쿠폰/재고/포인트 중 하나 실패 시 전체 롤백 | 미충족 | 쿠폰/포인트 처리 부재 |
| 주문 성공 시 모든 처리 정상 반영 | 부분 충족 | 현재 구현 범위(주문/재고/장바구니)만 증명 가능 |
| 좋아요/싫어요 동시 요청 시 카운트 정상 반영 | 미충족 | 동시성 테스트 실패(10 -> 7), 싫어요 동시 시나리오 부재 |
| 동일 쿠폰 동시 주문 시 단 1회 사용 | 미충족 | 쿠폰 구현/테스트 부재 |
| 동일 상품 동시 주문 시 재고 정상 차감 | 부분 충족 | 락 기반 설계는 존재, 실동시성 통합테스트 부재 |

## 7) 추가 기준 판정

### A. 클라이언트 기준 락 전략
- 주문 재고 차감: 비관적 락 적용은 목적에 부합 (`SELECT ... FOR UPDATE` 기반).
- 좋아요 카운트: 낙관적 락 선택 방향 자체는 빠른 응답에 맞지만, 현재 `maxAttempts=3` 정책으로 결과 정합성 실패가 발생해 사용자 기대 충족 실패.

### B. `analyze-query` 스킬 기준 "문제 0개" 달성 여부
- 판정: **미달성**
- 미달성 근거:
  - 불필요하게 큰 트랜잭션: 존재
  - 조회/쓰기 혼합 트랜잭션: 존재
  - DTO Projection 대신 Entity 조회(QueryDSL): 존재
  - Query 결과의 영속성 컨텍스트 이슈 가능성: 존재

## 8) 결론
- 현재 변경사항은 "좋아요 동시성 안정화"를 목표로 했으나, 실제 고경합 케이스에서 실패가 재현되어 요구사항을 충족하지 못한다.
- 주문 체크리스트에서 쿠폰/포인트 관련 항목은 현재 코드베이스 기준으로 증명 불가하다.
- 추가 기준(`analyze-query`)까지 포함하면, "문제 단 하나도 없어야 한다" 조건은 충족되지 않는다.
