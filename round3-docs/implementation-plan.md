# 5-Agent Parallel BC Implementation Plan + ArchUnit

## Context

현재 프로젝트는 **User BC만 구현**된 상태. 설계 문서(`docs/design/`)를 기반으로 4개 새로운 BC(Catalog, Like, Cart, Order)를 구현해야 한다. 병렬 에이전트 실행을 통해 효율적으로 진행하되, 공유 리소스 충돌을 방지하는 전략이 필요하다.

**ArchUnit**을 도입하여 CLAUDE.md의 아키텍처 규칙을 자동으로 검증한다.

---

## 확정된 결정 사항

| 항목 | 결정 |
|------|------|
| Admin 인증 | `X-Loopers-Ldap: loopers.admin` 헤더로 검증 |
| PageCriteria/PageResult | **각 도메인별 독립 생성** (중복 수용, 공유 위치 이동 안 함) |
| LIKES_DESC 정렬 | Product에 `likeCount` 필드 추가, 이벤트로 동기화 |
| BaseEntity 분리 | `BaseEntity`(id+timestamps) + `SoftDeleteBaseEntity`(+deletedAt, delete, restore) |
| ErrorType | **단일 enum 유지** + Common 에이전트가 선행 등록 |

### Order 삭제 정책 설명
Order는 Hard Delete/Soft Delete가 아닌 **삭제 불가(immutable record)**. P0에서 삭제 API 자체를 제공하지 않는다. 주문은 영구 기록이며, 주문 이후 상품이 삭제/수정되어도 스냅샷 기반으로 조회 가능해야 한다. BaseEntity를 상속하지만 `delete()` 메서드를 호출하지 않으며 `deletedAt`은 항상 null이다.

---

## ErrorType 분리 의견

**결론: 단일 enum 유지 + Common 에이전트가 모든 BC의 ErrorType을 선행 등록**

| 기준 | 단일 enum (현행) | BC별 enum 분리 |
|------|-----------------|---------------|
| GlobalExceptionHandler | 수정 불필요 | CoreException 구조 변경 필요 (interface 도입) |
| 기존 패턴 일관성 | 유지 | 위반 (User BC 포함 전면 리팩토링) |
| 병렬 충돌 | Common 에이전트가 선행 등록하면 해결 | 해결되지만 복잡도 증가 |
| BC 독립성 | 낮음 (수용 가능) | 높음 |
| 테스트 영향 | ErrorTypeTest 한 번 수정 | 각 BC별 테스트 + 기존 테스트 수정 |

**전략**: Common 에이전트가 **모든 BC의 ErrorType을 미리 등록**하고 ErrorTypeTest를 업데이트. BC 에이전트들은 이미 존재하는 ErrorType을 참조만 한다.

---

## Agent 의존 관계 및 실행 순서

```
Phase 0:  Agent 1 (Common) ─── 공유 인프라, ErrorType, ArchUnit, Admin 인증 검증
              │
Phase 1:  Agent 2 (Catalog) ── Brand + Product 도메인 (다른 BC들의 제공자)
              │
Phase 2:  Agent 3 (Like) ─────┐
          Agent 4 (Cart) ──────┤ 병렬 가능 (Port 인터페이스는 자체 정의, ACL만 Catalog 의존)
          Agent 5 (Order) ─────┘ Order는 Cart+Catalog 의존이지만, ACL 구현만 마지막에
```

**실질 병렬 가능 범위**:
- Phase 2의 Like/Cart/Order는 **도메인 모델~서비스 레이어**까지 병렬 가능
- **ACL 구현체**(infrastructure/acl/)는 Catalog의 JPA 엔티티/리포지토리를 참조하므로, Catalog 완성 후 구현

### Phase별 승인 + 커밋 워크플로우

**각 Phase 완료 시 반드시 아래 절차를 따른다:**

1. Phase 작업 완료 → 테스트 전체 통과 확인 (`./gradlew :apps:commerce-api:test`)
2. **사용자에게 결과 보고 및 승인 요청**
3. 사용자 승인 후 커밋 (커밋 메시지 컨벤션 준수)
4. 다음 Phase로 진행

**커밋 단위**: BC별 독립 커밋. 각 BC 완료 시 사용자 승인 후 커밋.

| 순서 | 커밋 내용 | 선행 조건 |
|------|----------|----------|
| 1 | Common (ErrorType, ArchUnit, Admin인증) | 없음 |
| 2 | Brand (Catalog BC - Brand 도메인) | Common 승인/커밋 완료 |
| 3 | Product (Catalog BC - Product 도메인) | Brand 승인/커밋 완료 |
| 4 | Like BC | Product 승인/커밋 완료 |
| 5 | Cart BC | Product 승인/커밋 완료 |
| 6 | Order BC | Like + Cart 승인/커밋 완료 |

**참고**: Brand와 Product는 같은 Catalog BC이지만, 규모가 크므로 별도 커밋으로 분리.
Like/Cart는 Product 완료 후 병렬 가능. Order는 Like+Cart ACL 의존이 있으므로 마지막.

### TDD 필수 준수

모든 구현은 **철저한 TDD (Red → Green → Refactor)** 워크플로우를 따른다:
- **Red**: 실패하는 테스트를 먼저 작성
- **Green**: 테스트를 통과하는 최소한의 코드 작성
- **Refactor**: 불필요 코드 제거, 품질 개선, 모든 테스트 통과 확인
- 테스트 없이 프로덕션 코드를 먼저 작성하지 않는다

---

## Agent 1: Common (공유 인프라 + ArchUnit)

**목적**: 모든 BC 에이전트가 시작하기 전에 공유 인프라 준비

### 1-1. PageCriteria/PageResult 정책

**각 도메인별 독립 생성** (중복 수용). User BC의 기존 `user/domain/repository/vo/PageCriteria.java`, `PageResult.java`는 그대로 유지.
새로운 BC(Brand, Product, Like, Cart, Order)에서 페이지네이션이 필요한 경우, 해당 BC의 `{domain}/domain/repository/vo/`에 동일 구조로 생성.

### 1-2. Admin 인증 인프라

Admin 헤더 검증 유틸 생성 (기존 `HeaderValidator` 패턴 참조):
- 생성: `support/common/AdminHeaderValidator.java` (or 기존 HeaderValidator 확장)
- 검증: `X-Loopers-Ldap` 헤더 값이 `loopers.admin`인지 확인
- 실패 시: `CoreException(AUTHENTICATION_FAILED)` (기존 에러 타입 재사용)

### 1-3. ErrorType 전체 등록

모든 BC에서 필요한 ErrorType을 미리 추가:

```java
/** Catalog - Brand 에러 */
BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "BRAND_NOT_FOUND", "브랜드가 존재하지 않습니다."),
BRAND_HAS_ACTIVE_PRODUCTS(HttpStatus.CONFLICT, "BRAND_HAS_ACTIVE_PRODUCTS", "해당 브랜드에 활성 상품이 존재하여 삭제할 수 없습니다."),
INVALID_BRAND_NAME(HttpStatus.BAD_REQUEST, "INVALID_BRAND_NAME", "올바른 브랜드명을 입력해주세요."),

/** Catalog - Product 에러 */
PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "상품이 존재하지 않습니다."),
INVALID_PRODUCT_NAME(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_NAME", "올바른 상품명을 입력해주세요."),
INVALID_PRODUCT_PRICE(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_PRICE", "올바른 가격을 입력해주세요."),
INVALID_PRODUCT_STOCK(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_STOCK", "올바른 재고 수량을 입력해주세요."),
PRODUCT_OUT_OF_STOCK(HttpStatus.CONFLICT, "PRODUCT_OUT_OF_STOCK", "재고가 부족합니다."),

/** Like 에러 */
LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "LIKE_NOT_FOUND", "좋아요가 존재하지 않습니다."),
LIKE_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "LIKE_TARGET_NOT_FOUND", "좋아요 대상이 존재하지 않습니다."),

/** Cart 에러 */
CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "장바구니 항목이 존재하지 않습니다."),
INVALID_CART_QUANTITY(HttpStatus.BAD_REQUEST, "INVALID_CART_QUANTITY", "수량은 1 이상이어야 합니다."),

/** Order 에러 */
ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문이 존재하지 않습니다."),
ORDER_EMPTY_ITEMS(HttpStatus.BAD_REQUEST, "ORDER_EMPTY_ITEMS", "주문 항목이 비어있습니다."),
ORDER_OUT_OF_STOCK(HttpStatus.CONFLICT, "ORDER_OUT_OF_STOCK", "재고가 부족하여 주문할 수 없습니다."),
```

수정 파일:
- `support/common/error/ErrorType.java` — enum 추가 (13 → ~28)
- `support/common/error/ErrorTypeTest.java` — provider + hasSize 업데이트

### 1-4. ArchUnit 의존성 추가

`apps/commerce-api/build.gradle.kts`에 추가:
```kotlin
testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
```

### 1-5. ArchUnit 테스트 작성

생성: `src/test/java/com/loopers/architecture/LayerDependencyArchTest.java`

| 규칙 | 검증 내용 |
|------|----------|
| Layer Direction | `domain`은 `application`, `infrastructure`, `interfaces`에 의존 금지 |
| Facade → Service Only | `..facade..`는 `..service..`만 의존 (repository, port 직접 호출 금지) |
| Controller → Facade Only | `..controller..`는 `..facade..`만 의존 |
| Domain Model Purity | `..domain.model..`는 Spring/JPA 프레임워크 의존 금지 |
| Repository Interface Purity | `..domain.repository..` 인터페이스는 Spring 타입(Page, Pageable 등) 사용 금지 |
| DomainService Purity | `..domain.service..`는 repository, port 의존 금지 |
| Cross-BC Boundary | 각 BC의 domain 레이어가 다른 BC의 domain을 직접 참조 금지 |

### 1-6. 수정/생성 파일 목록

```
# 수정
apps/commerce-api/build.gradle.kts                           (ArchUnit 의존성)
support/common/error/ErrorType.java                          (ErrorType 추가)
support/common/error/ErrorTypeTest.java                      (테스트 업데이트)

# 생성
support/common/AdminHeaderValidator.java                     (또는 기존 패턴 활용)
architecture/LayerDependencyArchTest.java
```

---

## Agent 2: Catalog BC (Brand + Product)

**목적**: 다른 BC들의 제공자. Brand CRUD + 삭제 정책, Product CRUD + 재고 관리 + likeCount

### 2-1. Brand 도메인

```
brand/domain/model/Brand.java                     # create(), reconstruct(), changeName(), changeDescription()
brand/domain/model/vo/BrandName.java              # record, 1-100자, trim
brand/domain/model/vo/BrandDescription.java       # record, nullable, max 500자
brand/domain/repository/BrandCommandRepository.java
brand/domain/repository/BrandQueryRepository.java
brand/domain/service/BrandDeleteValidator.java    # 순수 Java, 활성 상품 존재 시 예외
brand/domain/event/BrandDeletedEvent.java         # record(Long brandId)
```

### 2-2. Brand 인프라

```
brand/infrastructure/entity/BrandEntity.java       # BaseEntity 상속, from() + toDomain()
brand/infrastructure/jpa/BrandJpaRepository.java
brand/infrastructure/repository/BrandCommandRepositoryImpl.java
brand/infrastructure/repository/BrandQueryRepositoryImpl.java
```

### 2-3. Brand 애플리케이션

```
brand/application/dto/in/BrandCreateInDto.java
brand/application/dto/in/BrandUpdateInDto.java
brand/application/dto/out/BrandOutDto.java         # from(Brand)
brand/application/dto/out/BrandDetailOutDto.java   # from(Brand)
brand/application/service/BrandCommandService.java # deleteBrand: ProductQueryService → DeleteValidator → Repository → Event
brand/application/service/BrandQueryService.java   # getBrandById, existsById
brand/application/facade/BrandCommandFacade.java
brand/application/facade/BrandQueryFacade.java
brand/support/config/DomainServiceConfig.java      # @Bean BrandDeleteValidator
```

### 2-4. Brand 인터페이스

```
brand/interfaces/web/controller/BrandQueryController.java         # GET /api/v1/brands, GET /api/v1/brands/{id}
brand/interfaces/web/controller/BrandAdminCommandController.java  # POST, PUT, DELETE /api-admin/v1/brands
brand/interfaces/web/controller/BrandAdminQueryController.java    # GET /api-admin/v1/brands
brand/interfaces/web/request/BrandCreateRequest.java
brand/interfaces/web/request/BrandUpdateRequest.java
brand/interfaces/web/response/BrandResponse.java
brand/interfaces/web/response/BrandDetailResponse.java
brand/support/common/AdminHeaderValidator.java                    # (또는 공유 Common 것 사용)
```

Admin 컨트롤러에서 `X-Loopers-Ldap: loopers.admin` 헤더 검증.

### 2-5. Product 도메인

```
product/domain/model/Product.java                  # create(), reconstruct(), changeName/Price/Stock/Description, decreaseStock(), increaseLikeCount(), decreaseLikeCount()
product/domain/model/vo/ProductName.java           # record, 1-200자
product/domain/model/vo/Money.java                 # record BigDecimal, >= 0
product/domain/model/vo/Stock.java                 # record Long, >= 0
product/domain/model/vo/ProductDescription.java    # record, nullable, max 1000자
product/domain/model/enum/ProductSortType.java     # LATEST, PRICE_ASC, LIKES_DESC
product/domain/repository/ProductCommandRepository.java
product/domain/repository/ProductQueryRepository.java
product/domain/event/ProductDeletedEvent.java      # record(Long productId)
```

**likeCount 필드**: Product 도메인에 `likeCount` (Long, default 0) 추가. Like 생성/삭제 이벤트로 동기화. 정렬 시 별도 cross-BC 조인 불필요.

### 2-6. Product 인프라/애플리케이션/인터페이스

- ProductCommandService에서 `BrandQueryService.getBrandById()` 직접 호출 (같은 Catalog BC)
- Product 목록: brandId 필터, ProductSortType 정렬 (LATEST/PRICE_ASC/LIKES_DESC), 페이지네이션

### 2-7. Brand/Product 이벤트

- BrandCommandService.deleteBrand() → `publishEvent(new BrandDeletedEvent(brandId))`
- ProductCommandService.deleteProduct() → `publishEvent(new ProductDeletedEvent(productId))`
- Product 이벤트 리스너: Like 생성/삭제 이벤트 수신 → likeCount 증감
  - `product/interfaces/event/ProductLikeEventListener.java`

### 2-8. 테스트 (TDD)

각 도메인별:
- Domain: `BrandTest`, VO tests, `BrandDeleteValidatorTest`
- Service: `BrandCommandServiceTest`, `BrandQueryServiceTest`
- Facade: `BrandCommandFacadeTest`, `BrandQueryFacadeTest`
- Repository Integration: `BrandCommandRepositoryTest`, `BrandQueryRepositoryTest`
- Entity: `BrandEntityTest`
- Controller Unit: `BrandAdminCommandControllerTest`, `BrandQueryControllerTest`
- E2E: `BrandControllerE2ETest`
- Product도 동일 구조

---

## Agent 3: Like BC

**목적**: 좋아요 등록/취소/목록 조회, 멱등 처리, 이벤트 기반 정리

### 3-1. Like 도메인

```
like/domain/model/Like.java                        # create(), reconstruct()
like/domain/model/enum/LikeTargetType.java         # PRODUCT, BRAND
like/domain/repository/LikeCommandRepository.java  # save, delete, deleteAllByTarget
like/domain/repository/LikeQueryRepository.java    # findByUserAndTarget, findByUserId
```

### 3-2. Cross-BC Port

```
like/application/port/out/client/catalog/LikeTargetValidator.java    # interface: validateTarget(LikeTargetType, Long targetId)
like/infrastructure/acl/catalog/LikeTargetValidatorImpl.java         # Brand/Product JPA 직접 참조 (Catalog 완성 후)
```

### 3-3. Event Listener

```
like/interfaces/event/LikeEventListener.java
  - onProductDeleted(ProductDeletedEvent) → deleteAllByTarget(PRODUCT, productId)
  - onBrandDeleted(BrandDeletedEvent) → deleteAllByTarget(BRAND, brandId)
```

### 3-4. Like 이벤트 발행 (likeCount 동기화용)

```
like/domain/event/LikeCreatedEvent.java            # record(LikeTargetType, Long targetId)
like/domain/event/LikeCancelledEvent.java          # record(LikeTargetType, Long targetId)
```
→ Catalog BC의 ProductLikeEventListener가 수신하여 likeCount 증감

### 3-5. 서비스/퍼사드/컨트롤러

```
like/application/service/LikeCommandService.java   # createLike (멱등 - 이미 있으면 성공 반환), cancelLike (없으면 404)
like/application/service/LikeQueryService.java     # getMyLikes (target 필터: all/products/brands)
like/application/facade/LikeCommandFacade.java     # 인증 헤더 검증 + Service 호출
like/application/facade/LikeQueryFacade.java       # 인증 헤더 검증
like/interfaces/web/controller/LikeCommandController.java  # POST/DELETE /api/v1/{products|brands}/{id}/likes
like/interfaces/web/controller/LikeQueryController.java    # GET /api/v1/users/me/likes
```

### 3-6. Infrastructure + 테스트

- LikeEntity: BaseEntity 상속 (deletedAt 없음) (Hard Delete = 물리 삭제)
- 유니크 제약: `(user_id, target_type, target_id)`
- TDD: 전체 레이어 단위 테스트 + E2E

---

## Agent 4: Cart BC

**목적**: 장바구니 CRUD, 수량 병합, 선택, 이벤트 기반 정리

### 4-1. Cart 도메인

```
cart/domain/model/CartItem.java                    # create(), reconstruct(), addQuantity(), changeQuantity(), select(), deselect()
cart/domain/model/vo/Quantity.java                 # record Long, > 0
cart/domain/repository/CartItemCommandRepository.java  # save, delete, deleteAllByProductId, deleteAllByIds
cart/domain/repository/CartItemQueryRepository.java    # findByUserId, findById, findByUserIdAndProductId
```

### 4-2. Cross-BC Port

```
cart/application/port/out/client/catalog/CartProductReader.java     # interface: readProduct(Long productId)
cart/infrastructure/acl/catalog/CartProductReaderImpl.java           # Product JPA 직접 참조 (Catalog 완성 후)
```

### 4-3. Event Listener

```
cart/interfaces/event/CartEventListener.java
  - onOrderCreated(OrderCreatedEvent) → deleteByIds(userId, cartItemIds)
  - onProductDeleted(ProductDeletedEvent) → deleteByProductId(productId)
```

### 4-4. 서비스/퍼사드/컨트롤러

```
cart/application/service/CartItemCommandService.java  # addItem(동일상품 병합), updateQuantity, deleteItem, updateSelection
cart/application/service/CartItemQueryService.java    # getCart, getCartStatus (Cross-BC: 상품 재고 확인)
cart/application/facade/CartItemCommandFacade.java    # 인증 + 소유권 검증 (userId == cartItem.userId, 불일치 시 404)
cart/application/facade/CartItemQueryFacade.java      # 인증
cart/interfaces/web/controller/CartItemCommandController.java  # POST /api/v1/cart/items, PUT ./{id}, DELETE ./{id}, PUT ./selection
cart/interfaces/web/controller/CartItemQueryController.java    # GET /api/v1/cart, GET /api/v1/cart/status
```

### 4-5. Infrastructure + 테스트

- CartItemEntity: BaseEntity 상속 (deletedAt 없음) (Hard Delete)
- 유니크 제약: `(user_id, product_id)` — 동일 상품 중복 라인 방지
- TDD: 전체 레이어 단위 테스트 + E2E

---

## Agent 5: Order BC

**목적**: 주문 생성 (재고 차감 + 스냅샷 + 멱등성), 주문 조회 (User/Admin)

### 5-1. Order 도메인

```
order/domain/model/Order.java                      # create(userId, totalPrice), reconstruct()
order/domain/model/OrderItem.java                  # create(productId, snapshotName, snapshotPrice, quantity)
order/domain/model/IdempotencyKey.java             # create(userId, requestId, orderId), reconstruct()
order/domain/model/vo/SnapshotName.java            # record String, not blank
order/domain/model/vo/SnapshotPrice.java           # record BigDecimal, >= 0
order/domain/repository/OrderCommandRepository.java
order/domain/repository/OrderItemCommandRepository.java
order/domain/repository/OrderQueryRepository.java      # findByIdWithItems, findByUserId, findAll
order/domain/repository/IdempotencyKeyCommandRepository.java
order/domain/repository/IdempotencyKeyQueryRepository.java
order/domain/event/OrderCreatedEvent.java              # record(Long userId, List<Long> cartItemIds)
```

### 5-2. Cross-BC Ports

```
order/application/port/out/client/catalog/OrderProductReader.java     # readProducts(List<Long> productIds)
order/application/port/out/client/catalog/OrderStockManager.java      # decreaseStock(Long productId, long quantity)
order/application/port/out/client/cart/OrderCartItemReader.java       # readCartItems(Long userId, List<Long> cartItemIds)

order/infrastructure/acl/catalog/OrderProductReaderImpl.java          # Product JPA 직접 참조
order/infrastructure/acl/catalog/OrderStockManagerImpl.java           # SELECT FOR UPDATE + stock 감소
order/infrastructure/acl/cart/OrderCartItemReaderImpl.java            # CartItem JPA 직접 참조
```

### 5-3. 서비스/퍼사드/컨트롤러

```
order/application/service/OrderCommandService.java
  # createOrder 흐름:
  #   1. IdempotencyKey 확인 (중복 → 기존 주문 반환)
  #   2. OrderCartItemReader → 카트 항목 조회 + 소유권 검증
  #   3. OrderProductReader → 상품 정보 조회
  #   4. OrderStockManager → 재고 차감 (FOR UPDATE, All-or-Nothing)
  #   5. OrderItem 생성 (스냅샷: snapshotName, snapshotPrice)
  #   6. Order 생성 + OrderItems 저장
  #   7. IdempotencyKey 저장
  #   8. OrderCreatedEvent 발행

order/application/service/OrderQueryService.java       # getOrderForUser (소유권 검증), getOrdersForUser, getAllOrders (admin)
order/application/facade/OrderCommandFacade.java       # @Transactional (핵심 단일 트랜잭션: 재고차감+주문생성)
order/application/facade/OrderQueryFacade.java         # User: 소유권 검증, Admin: 소유권 미검증
order/interfaces/web/controller/OrderCommandController.java       # POST /api/v1/orders (인증: X-Loopers-LoginId/Pw)
order/interfaces/web/controller/OrderQueryController.java         # GET /api/v1/orders, GET /api/v1/orders/{id}
order/interfaces/web/controller/OrderAdminQueryController.java    # GET /api-admin/v1/orders (인증: X-Loopers-Ldap)
```

### 5-4. Infrastructure + 테스트

- OrderEntity, OrderItemEntity: BaseEntity 상속 (deletedAt 없음) (삭제 불가 - 영구 기록)
- IdempotencyKeyEntity: 유니크 제약 `(user_id, request_id)`
- 재고 차감: 비관적 락 (`SELECT ... FOR UPDATE`)
- TDD: 전체 레이어 + 동시성 테스트 (재고 경쟁 조건)

---

## 각 Agent 내부 TDD 순서

모든 에이전트가 동일한 순서를 따름:

1. **Domain Model + VOs** (Red → Green → Refactor)
2. **Domain Service** (해당 시)
3. **Repository Interface** 정의
4. **Infrastructure** (Entity, JPA, RepositoryImpl) + Integration Test
5. **Application Service** + Unit Test (Mockito)
6. **Application Facade** + Unit Test
7. **DTOs** (InDto/OutDto/Request/Response)
8. **Controller** + Unit Test (MockMvc)
9. **E2E Test** (@SpringBootTest + TestContainers)
10. **Cross-BC Port Interface** (정의)
11. **ACL Implementation** (마지막 - 다른 BC 의존)

---

## 검증 방법

```bash
# 1. 전체 테스트
./gradlew :apps:commerce-api:test

# 2. ArchUnit 규칙 검증
./gradlew :apps:commerce-api:test --tests "com.loopers.architecture.*"

# 3. 커버리지 리포트
./gradlew :apps:commerce-api:jacocoTestReport

# 4. 빌드
./gradlew :apps:commerce-api:build
```

---

## 참조 파일 (기존 User BC 패턴)

| 역할 | 경로 |
|------|------|
| Domain Model 패턴 | `user/domain/model/User.java` |
| VO 패턴 | `user/domain/model/vo/Password.java` |
| Repository Interface | `user/domain/repository/UserCommandRepository.java` |
| RepositoryImpl | `user/infrastructure/repository/UserCommandRepositoryImpl.java` |
| Entity 패턴 | `user/infrastructure/entity/UserEntity.java` |
| Facade 패턴 | `user/application/facade/UserCommandFacade.java` |
| Service 패턴 | `user/application/service/UserCommandService.java` |
| Controller 패턴 | `user/interfaces/web/controller/UserCommandController.java` |
| DTO (InDto) | `user/application/dto/in/UserSignUpInDto.java` |
| DTO (OutDto) | `user/application/dto/out/UserSignUpOutDto.java` |
| Request | `user/interfaces/web/request/UserSignUpRequest.java` |
| Response | `user/interfaces/web/response/UserSignUpResponse.java` |
| Port Interface | `user/application/port/out/util/PasswordEncoder.java` |
| HeaderValidator | `user/support/common/HeaderValidator.java` |
| E2E Test | `user/interfaces/UserControllerE2ETest.java` |
| ErrorType | `support/common/error/ErrorType.java` |
| ErrorTypeTest | `support/common/error/ErrorTypeTest.java` (hasSize + provider 패턴) |
| BaseEntity | `modules/jpa/src/main/java/com/loopers/domain/BaseEntity.java` |
