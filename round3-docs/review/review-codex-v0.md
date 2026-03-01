# Round3 Uncommitted Changes Review v1 (Codex)

- 리뷰 일시: 2026-02-23
- 워킹트리 범위: `git status --porcelain` 기준 커밋되지 않은 변경 전체 (`M=8`, `D=56`, `??=14`)
- 기준 소스:
  - `docs/design/01-requirements.md`
  - `docs/design/02-sequence-diagrams.md`
  - `docs/design/03-class-diagram.md`
  - `docs/design/04-erd-claude.md`
  - `round3-docs/round3-implementation-plan-updated.md`
  - `CLAUDE.md`
  - 관련 스킬 문서: `.claude/skills/create-endpoint/SKILL.md`, `.claude/skills/domain-model/SKILL.md`, `.claude/skills/error-handling/SKILL.md`, `.claude/skills/layered-architecture/SKILL.md`
- 실행 검증:
  - `./gradlew :apps:commerce-api:test --tests "com.loopers.architecture.LayerDependencyArchTest" --tests "com.loopers.engagement.like.application.service.LikeCommandServiceTest" --tests "com.loopers.ordering.order.application.service.OrderCommandServiceTest" --tests "com.loopers.ordering.order.application.facade.OrderCommandFacadeTest"` → `BUILD SUCCESSFUL`

---

## 1) 판정/점수 규칙

### 1.1 판정 타입
- `PASS`: 기준 충족
- `FAIL`: 기준 미충족
- `FAIL-DOC`: 문서/계획(또는 본 리뷰 체크리스트)이 90% 이상 타당하다고 판단되는 충돌 건. 점수는 `FAIL`로 반영
- `UNDECIDABLE`: 문서와 구현의 우열을 단정하기 어려운 건. 의견을 작성하고 점수 산정에서 제외

### 1.2 점수 산식
- 총 기준 점수는 100점
- `UNDECIDABLE` 배점은 분모에서 제외
- 산식: `최종점수 = 획득점수 / (100 - UNDECIDABLE 배점합) * 100`

### 1.3 합격선
- `85점 이상` + `Critical FAIL 0건`

---

## 2) 100점 검토 기준 (PASS/FAIL)

### 2.1 Product / Brand 도메인 (20점)

| ID | 배점 | PASS 기준 | FAIL 기준 |
|---|---:|---|---|
| PB-01 | 5 | 상품 정보 객체(목록/상세)가 브랜드 정보 + 좋아요 수를 함께 제공 | 브랜드 정보 또는 좋아요 수 누락/불완전 |
| PB-02 | 4 | 상품 정렬 계약(`latest`, `price_asc`, `likes_desc`)과 API 계약이 일치 | 정렬 값/파라미터 계약 불일치 |
| PB-03 | 3 | 상품이 재고를 가지며 주문 시 재고 차감 경로 존재 | 재고 필드/차감 경로 부재 |
| PB-04 | 3 | 음수 재고 방지가 도메인(엔티티/VO)에서 강제 | 서비스/인프라에서만 방어 |
| PB-05 | 3 | `likes_desc` 정렬 정확도를 위해 likeCount 동기화가 구현 | Like 이벤트와 Product likeCount 동기화 부재 |
| PB-06 | 2 | 브랜드 삭제 시 활성 상품 존재 여부 정책 검증 | 정책 검증 부재 또는 우회 가능 |

### 2.2 Like 도메인 (15점)

| ID | 배점 | PASS 기준 | FAIL 기준 |
|---|---:|---|---|
| LK-01 | 3 | 좋아요가 유저-대상 관계의 독립 도메인으로 분리 | Product/Brand 내부 부속 상태로 결합 |
| LK-02 | 3 | 상품 상세/목록 조회에서 좋아요 수 제공 | 조회 응답에서 like count 부재 |
| LK-03 | 3 | 단위 테스트에 좋아요 등록/취소 흐름(정상 + 취소 예외) 존재 | 핵심 흐름 테스트 누락 |
| LK-04 | 3 | 좋아요 목록 조회 `target=all/products/brands` 필터 제공 | 필터 계약 미구현 |
| LK-05 | 3 | 좋아요 생성 멱등 + 취소 미존재 404 + 삭제 이벤트 정리 | 멱등/정리 정책 누락 |

### 2.3 Order 도메인 (25점)

| ID | 배점 | PASS 기준 | FAIL 기준 |
|---|---:|---|---|
| OR-01 | 3 | 주문이 다수 상품과 수량을 포함 | 단일상품 고정/수량 미지원 |
| OR-02 | 3 | 주문 생성 시 재고 차감 수행 | 주문과 재고 차감 분리/누락 |
| OR-03 | 3 | 재고 부족 예외 흐름이 도메인에서 설계 | 재고 부족 시나리오 부재 |
| OR-04 | 4 | 단위 테스트가 정상 주문 + 예외 주문(특히 재고부족)을 모두 검증 | 정상/예외 중 일부만 검증 |
| OR-05 | 3 | `requestId` 기반 멱등 주문 처리 | 중복 주문 생성 가능 |
| OR-06 | 4 | 동시 주문 시 재고 정합성 보장(락/버전 + 충돌 처리) | 동시성 제어 부재 |
| OR-07 | 2 | 주문 목록 `startDate/endDate` 계약 지원 | 날짜 필터 계약 미구현 |
| OR-08 | 3 | 인증 주체 기반 사용자 식별(클라이언트 임의 userId 신뢰 금지) | 헤더 userId 직접 신뢰/위조 가능 |

### 2.4 Domain Service (20점)

| ID | 배점 | PASS 기준 | FAIL 기준 |
|---|---:|---|---|
| DS-01 | 6 | 도메인 내부 복합 규칙이 Domain Service로 분리 | 규칙 위치가 일관되지 않음 |
| DS-02 | 4 | 상품 상세에서 Product + Brand 조합을 Application Layer가 수행 | Controller/Infra에서 직접 조합 |
| DS-03 | 4 | 복합 유스케이스는 Application Layer에 있고, 도메인 규칙은 도메인으로 위임 | Application에 비즈니스 규칙 과다 내장 |
| DS-04 | 6 | Domain Service가 stateless + 동일 BC 내 협력 중심으로 설계 | 상태 보유/외부 의존 중심 설계 |

### 2.5 소프트웨어 아키텍처 & 설계 (20점)

| ID | 배점 | PASS 기준 | FAIL 기준 |
|---|---:|---|---|
| AR-01 | 3 | 의존 방향 `Application → Domain ← Infrastructure` 유지 | 역방향 의존 존재 |
| AR-02 | 3 | Application Layer가 유스케이스 orchestration 수행 | Controller/Infra가 orchestration 수행 |
| AR-03 | 3 | 핵심 비즈니스 로직이 Entity/VO/Domain Layer에 위치 | 서비스/컨트롤러로 로직 누수 |
| AR-04 | 3 | Repository 인터페이스는 Domain, 구현은 Infra에 위치 | 인터페이스/구현 계층 혼재 |
| AR-05 | 2 | 패키지 구조가 계층 + 도메인 기준 유지 | 도메인/계층 경계 혼재 |
| AR-06 | 2 | 단위 테스트가 외부 의존성을 Fake/Stub/Mock으로 분리 가능 | 테스트가 외부 의존에 강결합 |
| AR-07 | 2 | 아키텍처 자동검증(ArchUnit) 규칙이 실제 컨트롤러 패키지 전부 커버 | 일부 패키지 미포착 |
| AR-08 | 2 | 이벤트 정리 로직이 소유권 스코프(userId+ids)를 유지 | ID만으로 삭제하여 스코프 누락 |

---

## 3) 기준 적용 결과

### 3.1 Product / Brand (8 / 20)

| ID | 배점 | 판정 | 득점 | 근거 |
|---|---:|---|---:|---|
| PB-01 | 5 | FAIL | 0 | `apps/commerce-api/src/main/java/com/loopers/catalog/product/infrastructure/query/ProductQueryPortImpl.java:66` (`brandName = null`), `apps/commerce-api/src/test/java/com/loopers/catalog/product/application/facade/ProductQueryFacadeTest.java:102` |
| PB-02 | 4 | FAIL-DOC | 0 | 문서 계약: `docs/design/01-requirements.md:684`, `docs/design/02-sequence-diagrams.md:15`; 구현: `apps/commerce-api/src/main/java/com/loopers/catalog/product/interfaces/web/controller/ProductQueryController.java:34` (`sortType`) |
| PB-03 | 3 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/catalog/product/domain/model/Product.java:36`, `apps/commerce-api/src/main/java/com/loopers/ordering/order/application/service/OrderCommandService.java:142` |
| PB-04 | 3 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/catalog/product/domain/model/vo/Stock.java:63`, `apps/commerce-api/src/main/java/com/loopers/catalog/product/domain/model/vo/Stock.java:47` |
| PB-05 | 3 | FAIL-DOC | 0 | 계획: `round3-docs/round3-implementation-plan-updated.md:24`, `round3-docs/round3-implementation-plan-updated.md:412`; 구현 발행: `apps/commerce-api/src/main/java/com/loopers/engagement/like/application/service/LikeCommandService.java:61`; 수신기 부재(카탈로그) |
| PB-06 | 2 | PASS | 2 | `apps/commerce-api/src/main/java/com/loopers/catalog/brand/application/facade/BrandCommandFacade.java:69`, `apps/commerce-api/src/main/java/com/loopers/catalog/brand/domain/model/Brand.java:104` |

### 3.2 Like (12 / 15)

| ID | 배점 | 판정 | 득점 | 근거 |
|---|---:|---|---:|---|
| LK-01 | 3 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/engagement/like/domain/model/Like.java:25`, `apps/commerce-api/src/main/java/com/loopers/engagement/like/infrastructure/entity/LikeEntity.java:21` |
| LK-02 | 3 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/catalog/product/interfaces/web/response/ProductResponse.java:19`, `apps/commerce-api/src/main/java/com/loopers/catalog/product/interfaces/web/response/ProductDetailResponse.java:20` |
| LK-03 | 3 | PASS | 3 | `apps/commerce-api/src/test/java/com/loopers/engagement/like/application/service/LikeCommandServiceTest.java:66`, `apps/commerce-api/src/test/java/com/loopers/engagement/like/application/service/LikeCommandServiceTest.java:122`, `apps/commerce-api/src/test/java/com/loopers/engagement/like/application/service/LikeCommandServiceTest.java:144` |
| LK-04 | 3 | FAIL-DOC | 0 | 문서 계약: `docs/design/01-requirements.md:742`; 구현: `apps/commerce-api/src/main/java/com/loopers/engagement/like/interfaces/controller/LikeQueryController.java:35`, `apps/commerce-api/src/main/java/com/loopers/engagement/like/domain/repository/LikeQueryRepository.java:28` |
| LK-05 | 3 | PASS | 3 | 생성 멱등/취소404: `apps/commerce-api/src/main/java/com/loopers/engagement/like/application/service/LikeCommandService.java:47`, `apps/commerce-api/src/main/java/com/loopers/engagement/like/application/service/LikeCommandService.java:72`; 삭제 정리 이벤트: `apps/commerce-api/src/main/java/com/loopers/engagement/like/interfaces/event/LikeCleanupEventListener.java:28` |

### 3.3 Order (12 / 25)

| ID | 배점 | 판정 | 득점 | 근거 |
|---|---:|---|---:|---|
| OR-01 | 3 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/ordering/order/domain/model/Order.java:29`, `apps/commerce-api/src/main/java/com/loopers/ordering/order/domain/model/OrderItem.java:30` |
| OR-02 | 3 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/ordering/order/application/service/OrderCommandService.java:142`, `apps/commerce-api/src/main/java/com/loopers/ordering/order/infrastructure/acl/catalog/OrderStockManagerImpl.java:38` |
| OR-03 | 3 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/catalog/product/domain/model/vo/Stock.java:47` |
| OR-04 | 4 | FAIL | 0 | 정상/EmptyCart만 존재: `apps/commerce-api/src/test/java/com/loopers/ordering/order/application/service/OrderCommandServiceTest.java:205`, `apps/commerce-api/src/test/java/com/loopers/ordering/order/application/service/OrderCommandServiceTest.java:134`; 재고부족 단위테스트 부재 |
| OR-05 | 3 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/ordering/order/application/facade/OrderCommandFacade.java:39` |
| OR-06 | 4 | FAIL-DOC | 0 | 요구: `docs/design/02-sequence-diagrams.md:1327`, `round3-docs/round3-implementation-plan-updated.md:550`; 구현: `apps/commerce-api/src/main/java/com/loopers/ordering/order/infrastructure/acl/catalog/OrderStockManagerImpl.java:41`, `apps/commerce-api/src/main/java/com/loopers/catalog/product/infrastructure/jpa/ProductJpaRepository.java:19` |
| OR-07 | 2 | FAIL-DOC | 0 | 요구: `docs/design/01-requirements.md:834`; 구현: `apps/commerce-api/src/main/java/com/loopers/ordering/order/interfaces/web/controller/OrderQueryController.java:31`, `apps/commerce-api/src/main/java/com/loopers/ordering/order/domain/repository/OrderQueryRepository.java:24` |
| OR-08 | 3 | FAIL-DOC | 0 | `apps/commerce-api/src/main/java/com/loopers/ordering/order/interfaces/web/controller/OrderCommandController.java:34` (클라이언트 userId 신뢰), `apps/commerce-api/src/main/java/com/loopers/cart/cart/interfaces/web/controller/CartItemCommandController.java:55` (`loginId.hashCode` 사용) |

### 3.4 Domain Service (8 / 20, UNDECIDABLE 12)

| ID | 배점 | 판정 | 득점 | 근거 |
|---|---:|---|---:|---|
| DS-01 | 6 | UNDECIDABLE | 제외 | DomainService 권고: `docs/design/02-sequence-diagrams.md:335`, `docs/design/03-class-diagram.md:245`; 현재 결정: `round3-docs/round3-implementation-plan-updated.md:235` (브랜드 검증은 도메인 모델) |
| DS-02 | 4 | PASS | 4 | `apps/commerce-api/src/main/java/com/loopers/catalog/product/application/facade/ProductQueryFacade.java:40` |
| DS-03 | 4 | PASS | 4 | `apps/commerce-api/src/main/java/com/loopers/ordering/order/application/facade/OrderCommandFacade.java:46`, `apps/commerce-api/src/main/java/com/loopers/catalog/brand/application/facade/BrandCommandFacade.java:69` |
| DS-04 | 6 | UNDECIDABLE | 제외 | CLAUDE는 DomainService 점진 도입 허용: `CLAUDE.md:260`; 설계문서는 DomainService 명시: `docs/design/02-sequence-diagrams.md:381` |

### 3.5 아키텍처 & 설계 (16 / 20)

| ID | 배점 | 판정 | 득점 | 근거 |
|---|---:|---|---:|---|
| AR-01 | 3 | PASS | 3 | `apps/commerce-api/src/test/java/com/loopers/architecture/LayerDependencyArchTest.java:47` |
| AR-02 | 3 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/catalog/brand/application/facade/BrandCommandFacade.java:67`, `apps/commerce-api/src/main/java/com/loopers/ordering/order/application/facade/OrderCommandFacade.java:47` |
| AR-03 | 3 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/catalog/product/domain/model/vo/Stock.java:47`, `apps/commerce-api/src/main/java/com/loopers/cart/cart/domain/model/vo/Quantity.java:49`, `apps/commerce-api/src/main/java/com/loopers/ordering/order/domain/model/Order.java:79` |
| AR-04 | 3 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/catalog/product/domain/repository/ProductQueryRepository.java:9`, `apps/commerce-api/src/main/java/com/loopers/catalog/product/infrastructure/repository/ProductQueryRepositoryImpl.java:16` |
| AR-05 | 2 | PASS | 2 | `apps/commerce-api/src/main/java/com/loopers/catalog/product/domain/model/Product.java`, `apps/commerce-api/src/main/java/com/loopers/ordering/order/application/service/OrderCommandService.java` |
| AR-06 | 2 | PASS | 2 | `apps/commerce-api/src/test/java/com/loopers/cart/cart/support/CartTestPortConfig.java:17`, `apps/commerce-api/src/test/java/com/loopers/engagement/like/support/LikeTestPortConfig.java:29`, `apps/commerce-api/src/test/java/com/loopers/ordering/order/interfaces/OrderPortTestConfig.java:23` |
| AR-07 | 2 | FAIL-DOC | 0 | ArchUnit 대상: `apps/commerce-api/src/test/java/com/loopers/architecture/LayerDependencyArchTest.java:117`; 실제 Like 컨트롤러: `apps/commerce-api/src/main/java/com/loopers/engagement/like/interfaces/controller/LikeCommandController.java:16` |
| AR-08 | 2 | FAIL-DOC | 0 | 이벤트에 userId 포함: `apps/commerce-api/src/main/java/com/loopers/ordering/order/domain/event/OrderCreatedEvent.java:13`; 정리 시 미사용: `apps/commerce-api/src/main/java/com/loopers/cart/cart/interfaces/event/CartCleanupEventListener.java:36`; 계획: `round3-docs/round3-implementation-plan-updated.md:471` |

---

## 4) 사용자 추가 체크리스트 1:1 매핑

### 4.1 Product / Brand

| 체크리스트 원문 | 매핑 기준 | 판정 | 배점 | 근거 |
|---|---|---|---:|---|
| 상품 정보 객체는 브랜드 정보, 좋아요 수를 포함한다 | PB-01 | FAIL | 5 | `apps/commerce-api/src/main/java/com/loopers/catalog/product/infrastructure/query/ProductQueryPortImpl.java:66` |
| 상품의 정렬 조건(`latest`, `price_asc`, `likes_desc`)을 고려한 조회 기능을 설계했다 | PB-02 | FAIL-DOC | 4 | `docs/design/01-requirements.md:684`, `apps/commerce-api/src/main/java/com/loopers/catalog/product/interfaces/web/controller/ProductQueryController.java:34` |
| 상품은 재고를 가지고 있고, 주문 시 차감할 수 있어야 한다 | PB-03 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/catalog/product/domain/model/Product.java:36`, `apps/commerce-api/src/main/java/com/loopers/ordering/order/application/service/OrderCommandService.java:142` |
| 재고의 음수 방지 처리는 도메인 레벨에서 처리된다 | PB-04 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/catalog/product/domain/model/vo/Stock.java:63` |

### 4.2 Like

| 체크리스트 원문 | 매핑 기준 | 판정 | 배점 | 근거 |
|---|---|---|---:|---|
| 좋아요는 유저와 상품 간의 관계로 별도 도메인으로 분리했다 | LK-01 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/engagement/like/domain/model/Like.java:25` |
| 상품의 좋아요 수는 상품 상세/목록 조회에서 함께 제공된다 | LK-02 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/catalog/product/interfaces/web/response/ProductResponse.java:19` |
| 단위 테스트에서 좋아요 등록/취소 흐름을 검증했다 | LK-03 | PASS | 3 | `apps/commerce-api/src/test/java/com/loopers/engagement/like/application/service/LikeCommandServiceTest.java:66` |

### 4.3 Order

| 체크리스트 원문 | 매핑 기준 | 판정 | 배점 | 근거 |
|---|---|---|---:|---|
| 주문은 여러 상품을 포함할 수 있으며, 각 상품의 수량을 명시한다 | OR-01 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/ordering/order/domain/model/Order.java:29` |
| 주문 시 상품의 재고 차감을 수행한다 | OR-02 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/ordering/order/application/service/OrderCommandService.java:142` |
| 재고 부족 예외 흐름을 고려해 설계되었다 | OR-03 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/catalog/product/domain/model/vo/Stock.java:47` |
| 단위 테스트에서 정상 주문 / 예외 주문 흐름을 모두 검증했다 | OR-04 | FAIL | 4 | `apps/commerce-api/src/test/java/com/loopers/ordering/order/application/service/OrderCommandServiceTest.java:205` |

### 4.4 Domain Service

| 체크리스트 원문 | 매핑 기준 | 판정 | 배점 | 근거 |
|---|---|---|---:|---|
| 도메인 내부 규칙은 Domain Service에 위치시켰다 | DS-01 | UNDECIDABLE | 6 | `docs/design/03-class-diagram.md:245`, `round3-docs/round3-implementation-plan-updated.md:235` |
| 상품 상세 조회 시 Product + Brand 정보 조합은 Application Layer에서 처리했다 | DS-02 | PASS | 4 | `apps/commerce-api/src/main/java/com/loopers/catalog/product/application/facade/ProductQueryFacade.java:40` |
| 복합 유스케이스는 Application Layer에 존재하고, 도메인 로직은 위임되었다 | DS-03 | PASS | 4 | `apps/commerce-api/src/main/java/com/loopers/ordering/order/application/facade/OrderCommandFacade.java:47` |
| 도메인 서비스는 상태 없이, 동일한 도메인 경계 내의 도메인 객체의 협력 중심으로 설계되었다 | DS-04 | UNDECIDABLE | 6 | `CLAUDE.md:260`, `docs/design/02-sequence-diagrams.md:381` |

### 4.5 소프트웨어 아키텍처 & 설계

| 체크리스트 원문 | 매핑 기준 | 판정 | 배점 | 근거 |
|---|---|---|---:|---|
| 전체 프로젝트의 구성은 `Application → Domain ← Infrastructure` 기반 | AR-01 | PASS | 3 | `apps/commerce-api/src/test/java/com/loopers/architecture/LayerDependencyArchTest.java:47` |
| Application Layer는 도메인 객체를 조합해 orchestration 했다 | AR-02 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/catalog/brand/application/facade/BrandCommandFacade.java:67` |
| 핵심 비즈니스 로직은 Entity, VO, Domain Service에 위치한다 | AR-03 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/catalog/product/domain/model/vo/Stock.java:47` |
| Repository Interface는 Domain Layer, 구현체는 Infra에 위치한다 | AR-04 | PASS | 3 | `apps/commerce-api/src/main/java/com/loopers/catalog/product/domain/repository/ProductQueryRepository.java:9`, `apps/commerce-api/src/main/java/com/loopers/catalog/product/infrastructure/repository/ProductQueryRepositoryImpl.java:16` |
| 패키지는 계층 + 도메인 기준으로 구성되었다 | AR-05 | PASS | 2 | `apps/commerce-api/src/main/java/com/loopers/catalog/product/domain/model/Product.java`, `apps/commerce-api/src/main/java/com/loopers/ordering/order/application/service/OrderCommandService.java` |
| 테스트는 외부 의존성을 분리하고 Fake/Stub 등으로 단위 테스트가 가능하게 구성되었다 | AR-06 | PASS | 2 | `apps/commerce-api/src/test/java/com/loopers/cart/cart/support/CartTestPortConfig.java:17`, `apps/commerce-api/src/test/java/com/loopers/ordering/order/interfaces/OrderPortTestConfig.java:23` |

---

## 5) FAIL-DOC 분류 (문서/계획 우세, 점수 반영)

1. 상품 정렬 API 계약 불일치
- 문서: `sort=latest|price_asc|likes_desc` (`docs/design/01-requirements.md:684`)
- 구현: `sortType=LATEST|PRICE_ASC|LIKES_DESC` (`apps/commerce-api/src/main/java/com/loopers/catalog/product/interfaces/web/controller/ProductQueryController.java:34`)

2. Like -> Product likeCount 동기화 누락
- 계획: Like 이벤트를 Catalog가 수신해 likeCount 증감 (`round3-docs/round3-implementation-plan-updated.md:412`)
- 구현: Like는 발행하지만 Catalog 수신기 없음 (`apps/commerce-api/src/main/java/com/loopers/engagement/like/application/service/LikeCommandService.java:61`)

3. 좋아요 목록 `target` 필터 미구현
- 문서: `target=all/products/brands` (`docs/design/01-requirements.md:742`)
- 구현: 목록 API에 `target` 파라미터 없음 (`apps/commerce-api/src/main/java/com/loopers/engagement/like/interfaces/controller/LikeQueryController.java:35`)

4. 재고 차감 동시성 제어 미구현
- 문서/계획: `FOR UPDATE` 또는 동등한 락 전략 요구 (`docs/design/02-sequence-diagrams.md:1327`, `round3-docs/round3-implementation-plan-updated.md:550`)
- 구현: 일반 조회 후 저장 (`apps/commerce-api/src/main/java/com/loopers/ordering/order/infrastructure/acl/catalog/OrderStockManagerImpl.java:41`)

5. 주문 날짜 필터 계약 미구현
- 문서: `startDate/endDate` (`docs/design/01-requirements.md:834`)
- 구현: API/리포지토리에 날짜 파라미터 없음 (`apps/commerce-api/src/main/java/com/loopers/ordering/order/interfaces/web/controller/OrderQueryController.java:31`)

6. 사용자 식별 무결성 취약
- 구현: `X-Loopers-UserId` 신뢰 (`apps/commerce-api/src/main/java/com/loopers/ordering/order/interfaces/web/controller/OrderCommandController.java:34`)
- 구현: Cart에서 `loginId.hashCode()`를 userId로 사용 (`apps/commerce-api/src/main/java/com/loopers/cart/cart/interfaces/web/controller/CartItemCommandController.java:55`)

7. ArchUnit 컨트롤러 규칙 블라인드 스팟
- 규칙 대상: `interfaces.web.controller` 한정 (`apps/commerce-api/src/test/java/com/loopers/architecture/LayerDependencyArchTest.java:117`)
- 실제 Like 컨트롤러 위치: `interfaces/controller` (`apps/commerce-api/src/main/java/com/loopers/engagement/like/interfaces/controller/LikeCommandController.java:16`)

8. 주문 후 Cart 정리에서 userId 스코프 미사용
- 이벤트 payload: `orderId, userId, cartItemIds` (`apps/commerce-api/src/main/java/com/loopers/ordering/order/domain/event/OrderCreatedEvent.java:13`)
- 실제 삭제: `cartItemIds`만 사용 (`apps/commerce-api/src/main/java/com/loopers/cart/cart/interfaces/event/CartCleanupEventListener.java:36`)

---

## 6) UNDECIDABLE 분류 (점수 제외 + 의견)

1. DS-01: 도메인 규칙의 Domain Service 강제 여부 (6점 제외)
- 설계 문서는 `BrandDeleteValidator`를 권장 (`docs/design/02-sequence-diagrams.md:335`, `docs/design/03-class-diagram.md:245`)
- 구현 계획 현행본은 `Brand.validateDeletable(...)`를 명시 (`round3-docs/round3-implementation-plan-updated.md:235`)
- 의견: 현재 코드베이스는 “단순 규칙은 도메인 모델 우선”으로 수렴되어 있음. 규칙 복잡도 증가 시 Domain Service로 재분리하는 정책 합의가 필요

2. DS-04: Domain Service stateless 협력 모델 적용 범위 (6점 제외)
- CLAUDE는 Domain Service를 “필요 시 점진 분리”로 정의 (`CLAUDE.md:260`)
- 설계 보조 문서는 DomainService 중심 기술 (`docs/design/02-sequence-diagrams.md:381`)
- 의견: 팀 표준 문서 우선순위를 명확히 정하지 않으면 동일 이슈 재발 가능

3. 주문 생성 입력 모델 (`cartItemIds` 직접입력 vs selectedCartItems 기반) (참고 분류)
- 요구사항 문서 예시: `cartItemIds + requestId` (`docs/design/01-requirements.md:851`)
- 구현: `requestId`만 받고 selected 항목 사용 (`apps/commerce-api/src/main/java/com/loopers/ordering/order/interfaces/web/request/OrderCreateRequest.java:11`)
- 의견: UX 단순성과 API 명시성의 트레이드오프. 제품 정책으로 확정 필요

---

## 7) 비즈니스/운영 엣지 케이스 검토

### Product / Brand
- `likes_desc` 정렬 왜곡: Like 이벤트는 발행되나 Product likeCount가 갱신되지 않아 랭킹이 장기적으로 부정확해질 수 있음
- 정렬 파라미터 계약 불일치: 클라이언트가 문서대로 `sort=price_asc`를 호출하면 400/의도치 않은 기본정렬로 이어질 가능성

### Like
- `target` 필터 부재: 좋아요가 많은 사용자에서 `users/me/likes` 응답이 과대해져 모바일/네트워크 비용 증가
- 삭제 이벤트 처리 실패 시 고아 데이터: 현재는 정리 리스너가 있으나 재시도/모니터링 기준이 문서화되어 있지 않음

### Order
- 사용자 식별 위변조: `X-Loopers-UserId` 직접 신뢰 시 타인 주문 조회/생성 시도 여지
- 동시 주문 초과판매: 락 부재로 재고 race condition 발생 가능
- CS/정산 조회 불편: 기간 필터 미지원으로 운영 조회 성능/가독성 저하
- 잘못된 이벤트 페이로드 전파 시 오삭제: `cartItemIds` 단독 삭제는 소유권 방어가 약함

### Domain Service / Architecture
- 표준 충돌: 동일 규칙(브랜드 삭제 검증)의 위치에 대한 문서 간 기준 불일치
- 아키텍처 테스트 공백: Like 컨트롤러 계층 위반이 있어도 ArchUnit이 탐지하지 못할 가능성

---

## 8) 점수 집계

| 구분 | 만점 | 획득 | 비고 |
|---|---:|---:|---|
| Product / Brand | 20 | 8 | FAIL-DOC 2건 |
| Like | 15 | 12 | FAIL-DOC 1건 |
| Order | 25 | 12 | FAIL-DOC 3건, FAIL 1건 |
| Domain Service | 20 | 8 | UNDECIDABLE 12점 제외 |
| Architecture | 20 | 16 | FAIL-DOC 2건 |
| **합계(원점수)** | **100** | **56** |  |

- `UNDECIDABLE` 제외 배점: `12점`
- 유효 분모: `88점`
- 최종 점수: `56 / 88 * 100 = 63.6점`
- 결과: **불합격** (`85점 미만`, Critical FAIL 존재)

---

## 9) 우선순위 조치 (P0/P1/P2)

### P0 (즉시)
1. 주문/장바구니 사용자 식별 체계 통일
- `X-Loopers-UserId` 직접 신뢰 제거
- 인증 결과 사용자 ID 단일 소스로 전환

2. 재고 차감 동시성 제어 도입
- `SELECT ... FOR UPDATE` 또는 낙관락 + 충돌 처리(409)
- 재고 경쟁 동시성 테스트 추가

3. Cart 정리 이벤트에 user 스코프 반영
- `deleteByIds(userId, cartItemIds)` 형태로 방어 강화

### P1 (단기)
4. 상품 정렬 API 계약 정합화
- `sort`/lowercase 규약 또는 문서/코드 일괄 정정

5. Like 목록 `target` 필터 구현

6. 주문 목록 `startDate/endDate` 필터 구현

7. Like 이벤트 -> Product likeCount 동기화 구현

### P2 (중기)
8. Domain Service 기준 문서 우선순위 확정
- `docs/design` vs `round3-implementation-plan-updated.md` vs `CLAUDE.md` 충돌 해소

9. ArchUnit 규칙 확장
- `interfaces.controller` 패키지 포함

10. E2E의 ACL 실연동 시나리오 분리
- 현재 Stub 기반 E2E와 별개로 “실제 ACL 경로” 검증 세트 추가
