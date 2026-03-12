# 프로덕션 코드 문서 준수 점검 결과

- 점검 일시: 2026-03-09
- 점검 대상: `apps/commerce-api/src/main/java` 프로덕션 코드
- 기준 문서:
  - `docs/design/01-requirements.md`
  - `docs/design/05-concurrency-strategy.md`
  - `CLAUDE.md`
  - `round4-docs/07-transaction-query-analysis-result.md`
  - `round4-docs/08-authentication-resolver-refactoring.md`
  - `round4-docs/03-sync-vs-event-analysis.md`

## 결론 요약

- 구조적 레이어 규칙(Controller -> Facade -> Service -> Repository/Port, ACL thin adapter, Cross-BC 경계)은 대체로 잘 지켜지고 있다.
- 다만 아래 4건은 문서와 실제 구현이 충돌하거나, 문서 의도와 다르게 동작할 가능성이 높은 항목으로 확인됐다.

## 주요 위반 및 리스크

### 1. 쿠폰 발급 중복 가드가 실패 요청까지 10분간 잠가버림

- 관련 코드:
  - `apps/commerce-api/src/main/java/com/loopers/coupon/issuedcoupon/application/facade/IssuedCouponCommandFacade.java:33`
  - `apps/commerce-api/src/main/java/com/loopers/coupon/issuedcoupon/application/service/IssuedCouponCommandService.java:40`
  - `apps/commerce-api/src/main/java/com/loopers/coupon/issuedcoupon/infrastructure/cache/CaffeineCouponIssueDuplicateGuard.java:24`
- 문제:
  - 로컬 캐시 가드를 템플릿 검증/DB 저장보다 먼저 점유한다.
  - 이후 검증 실패(삭제된 템플릿, 일시적 DB 오류 등)가 나더라도 캐시 해제 로직이 없다.
  - 그 결과 "실제로는 발급되지 않은 요청"도 이후 10분 동안 `COUPON_ISSUE_DUPLICATED`(409)로 막힐 수 있다.
- 충돌 문서:
  - `docs/design/01-requirements.md:984`
    - 중복 발급 시도만 409여야 한다.
  - `docs/design/05-concurrency-strategy.md:68`
    - 쿠폰 발급은 `1차 로컬 캐시 -> 2차 DB 존재 확인 -> 3차 UNIQUE 제약` 구조이며, race 시 500 허용이 기준이다.
- 영향:
  - 정상 재시도 요청이 잘못 409로 바뀔 수 있다.
  - 중복 발급 정책이 아니라 "최근 시도 자체"를 막는 동작이 된다.

### 2. 쿠폰 사용 경로가 현재 동시성 전략 문서와 다르게 비관적 락을 유지함

- 관련 코드:
  - `apps/commerce-api/src/main/java/com/loopers/coupon/issuedcoupon/application/service/IssuedCouponCommandService.java:61`
  - `apps/commerce-api/src/main/java/com/loopers/coupon/issuedcoupon/infrastructure/jpa/IssuedCouponJpaRepository.java:29`
  - `apps/commerce-api/src/main/java/com/loopers/ordering/order/infrastructure/acl/coupon/OrderCouponApplierImpl.java:31`
- 문제:
  - 쿠폰 적용 시 `findByIdForUpdate()`로 `PESSIMISTIC_WRITE`를 사용한다.
  - 현재 설계 기준 문서는 쿠폰 사용에 대해 "락 불필요"로 정리하고 있다.
- 충돌 문서:
  - `docs/design/05-concurrency-strategy.md:57`
    - `쿠폰 사용 | UPDATE | 락 불필요`
- 영향:
  - 문서와 구현 전략이 불일치한다.
  - 주문 트랜잭션에서 불필요한 락 대기/복잡도를 유발할 수 있다.

### 3. 정리성 부수효과가 여전히 동기 처리이며, 이벤트 기반 최종 목표가 미반영

- 관련 코드:
  - `apps/commerce-api/src/main/java/com/loopers/ordering/order/application/service/OrderCommandService.java:48`
  - `apps/commerce-api/src/main/java/com/loopers/catalog/product/application/facade/ProductCommandFacade.java:80`
  - `apps/commerce-api/src/main/java/com/loopers/catalog/brand/application/facade/BrandCommandFacade.java:68`
- 현재 동기 처리 중인 항목:
  - 주문 생성 후 장바구니 정리
  - 상품 삭제 후 좋아요 정리
  - 상품 삭제 후 장바구니 정리
  - 브랜드 삭제 후 좋아요 정리
- 충돌 문서:
  - `round4-docs/03-sync-vs-event-analysis.md:63`
  - `round4-docs/03-sync-vs-event-analysis.md:84`
  - `round4-docs/03-sync-vs-event-analysis.md:104`
  - `round4-docs/03-sync-vs-event-analysis.md:123`
- 확인 사항:
  - `catalog`, `ordering`, `engagement` 패키지에서 `@TransactionalEventListener` 기반 구현을 찾지 못했다.
- 영향:
  - 문서상 최종 목표와 구현이 어긋난다.
  - Cross-BC 쓰기 작업이 원 트랜잭션에 계속 묶인다.

### 4. Command Facade의 선행 조회가 여전히 write 트랜잭션 안에 포함됨

- 관련 코드:
  - `apps/commerce-api/src/main/java/com/loopers/catalog/product/application/facade/ProductCommandFacade.java:38`
  - `apps/commerce-api/src/main/java/com/loopers/engagement/productlike/application/facade/ProductLikeCommandFacade.java:30`
  - `apps/commerce-api/src/main/java/com/loopers/engagement/brandlike/application/facade/BrandLikeCommandFacade.java:29`
  - `apps/commerce-api/src/main/java/com/loopers/cart/cart/application/facade/CartItemCommandFacade.java:50`
- 문제:
  - 사전 조회, 대상 검증, 기존 좋아요 조회 같은 read 성격 작업이 write 트랜잭션 안에서 실행된다.
- 충돌 문서:
  - `round4-docs/07-transaction-query-analysis-result.md:115`
    - 대부분의 Command Facade에서 readOnly 로직이 write TX에 포함되는 문제를 이미 지적했다.
- 영향:
  - readOnly 힌트가 무시된다.
  - 트랜잭션 범위가 불필요하게 커진다.

## 준수 확인 항목

### 1. 레이어 및 ACL 규칙

- 다음 규칙들은 전반적으로 잘 지켜지고 있다.
  - Facade가 Repository/ACL 구현체를 직접 호출하지 않음
  - ACL이 Provider BC의 Facade만 참조함
  - Domain Repository 인터페이스가 Spring Data 타입을 외부로 노출하지 않음
  - Controller가 Response record를 사용하고 `Map<String, Object>`를 반환하지 않음
- 근거:
  - `apps/commerce-api/src/test/java/com/loopers/architecture/LayerDependencyArchTest.java`

### 2. 인증 책임 이동

- `AuthenticationResolver` 기반으로 Controller 진입점에서 인증을 처리하는 구조는 문서와 일치한다.
- 관련 코드:
  - `apps/commerce-api/src/main/java/com/loopers/support/common/auth/AuthenticationResolver.java`
  - `apps/commerce-api/src/main/java/com/loopers/cart/cart/interfaces/web/controller/CartItemCommandController.java`
  - `apps/commerce-api/src/main/java/com/loopers/ordering/order/interfaces/web/controller/OrderCommandController.java`
- 근거 문서:
  - `round4-docs/08-authentication-resolver-refactoring.md:25`

### 3. 좋아요 수 동기화 방식

- 좋아요 수 증감은 이벤트가 아니라 동기 + 원자적 카운터 방식으로 구현되어 있다.
- 이는 현재 최종 문서 기준과는 일치한다.
- 관련 코드:
  - `apps/commerce-api/src/main/java/com/loopers/engagement/productlike/application/facade/ProductLikeCommandFacade.java:30`
  - `apps/commerce-api/src/main/java/com/loopers/catalog/product/application/service/ProductCommandService.java:127`
- 근거 문서:
  - `docs/design/05-concurrency-strategy.md:59`
  - `round4-docs/03-sync-vs-event-analysis.md:37`

## 검증 수행 내역

### 확인한 문서

- `docs/design/01-requirements.md`
- `docs/design/02-sequence-diagrams.md`
- `docs/design/03-class-diagram.md`
- `docs/design/04-erd-claude.md`
- `docs/design/05-concurrency-strategy.md`
- `CLAUDE.md`
- `round4-docs/03-sync-vs-event-analysis.md`
- `round4-docs/07-transaction-query-analysis-result.md`
- `round4-docs/08-authentication-resolver-refactoring.md`

### 실행한 검증

- ArchUnit:
  - `./gradlew :apps:commerce-api:test --tests com.loopers.architecture.LayerDependencyArchTest`
  - 결과: 통과
- 벤치마크 분리 확인:
  - `apps/commerce-api/build.gradle.kts` 에 `benchmark` source set과 `benchmarkTest` 태스크가 추가되어 있음
  - 기본 `:apps:commerce-api:test` 태스크는 `src/test/java` 만 대상으로 실행되며 `src/benchmark/java` 는 포함하지 않음
- 벤치마크 제외 후 재실행:
  - `./gradlew :apps:commerce-api:test`
  - 결과: 실패
  - 집계: `1015 tests completed, 12 failed`
  - 리포트: `apps/commerce-api/build/reports/tests/test/index.html`
  - 실패 테스트 요약:
    - `com.loopers.catalog.product.infrastructure.query.ProductQueryPortImplTest$SearchAdminProductsTest` 4건 실패
    - `com.loopers.catalog.product.infrastructure.query.ProductQueryPortImplTest$SearchProductsTest` 7건 실패
    - `com.loopers.catalog.product.interfaces.ProductControllerE2ETest$GetAdminProductsTest` 1건 실패

### 검증 한계

- 이번 결과는 문서 대 코드 대조 + ArchUnit + 벤치마크 제외 기본 테스트 실행 결과를 함께 반영했다.
- 벤치마크 분리 후에도 이전 주요 지적 사항 4건은 모두 프로덕션 코드와 설계/개발 문서 간 불일치에서 나온 것이므로, 벤치마크 제외 여부와 무관하게 유지된다.

## 후속 조치 우선순위 제안

1. 쿠폰 발급 로컬 가드 해제/점유 시점 수정
2. 쿠폰 사용 락 전략을 문서 또는 코드 중 하나로 정렬
3. 이벤트 기반 정리 작업 도입 여부 확정 후 코드/문서 동기화
4. Command Facade의 선행 조회를 TX 밖으로 뺄지 재정리
