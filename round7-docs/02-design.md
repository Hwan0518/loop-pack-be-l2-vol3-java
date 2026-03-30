# Round 7 - Event & Kafka Pipeline 구현 계획

## Context
현재 모든 주문/좋아요 플로우가 단일 트랜잭션에서 동기 처리된다. 이번 라운드에서:
1. ApplicationEvent로 핵심/부가 로직을 분리하고
2. Kafka Outbox 패턴으로 시스템 간 이벤트 파이프라인을 구축하며
3. Kafka 기반 선착순 쿠폰 발급을 구현한다.

> **명칭**: 요구사항 문서의 `commerce-collector`는 역할 설명이며, 실제 앱 이름은 `commerce-streamer` (`apps/commerce-streamer/`). 본 설계에서 `commerce-streamer`는 요구사항의 `commerce-collector` 역할을 수행한다.

---

## 확정된 의사결정

### D1. 주문 TX 분리 기준
- **결정**: 장바구니 삭제만 이벤트로 분리
- **근거**: 쿠폰 적용은 가격 계산에 직접 영향 (totalPrice = originalTotalPrice - discountAmount). 장바구니 삭제 실패는 주문 무결성에 영향 없음.
- **핵심 TX 유지**: 재고차감 + 쿠폰적용 + 주문저장
- **AFTER_COMMIT 이벤트**: 장바구니 삭제, 유저 행동 로깅

### D2. 좋아요 집계 - product_metrics 통합
- **결정**: product_metrics가 SoT. ReadModelSyncConsumer가 주 writer, batch는 reconciliation 안전망.
- **근거**: 좋아요 집계는 실시간성을 크게 요구하는 기능이 아님. 데이터 흐름 단순화.
- **Step 1**: 이벤트 분리만 (Listener가 기존 경로로 ReadModel.likeCount 업데이트)
- **Step 2**: Outbox → Kafka → streamer → product_metrics. Listener의 ReadModel 업데이트 제거.
- **ReadModel 반영**: ReadModelSyncConsumer(commerce-streamer)가 snapshot 이벤트 소비 → ProductReadModel 반영 (version 비교). commerce-batch는 reconciliation 안전망.

### D3. 선착순 쿠폰 동시성 제어
- **결정**: 단일 파티션 순차 처리 (Kafka MQ 기반)
- **근거**:
  - **Redis vs MQ 선택 기준**: 둘 다 대용량 트래픽에서 동시성을 제어하기 위해 사용되지만, 성격이 다르다. Redis는 즉시 판정을 내려줘야 하는 문제에 적절하고 (예: 재고 차감, 실시간 잔량 조회), MQ는 접수만 보장하고 후처리가 가능한 문제에서 사용한다 (예: 쿠폰 발급 요청 → 결과 polling).
  - **선착순 쿠폰은 MQ에 적합**: 사용자는 발급 "요청"만 하고 결과를 나중에 확인한다 (202 Accepted → polling). 즉시 발급 여부를 응답할 필요가 없으므로, 접수 후 순차 처리하는 MQ 패턴이 자연스럽다.
  - **구현**: couponTemplateId를 partition key로 사용 → 같은 쿠폰의 모든 요청이 한 파티션에 적재 → Consumer가 순차 처리하므로 자연스러운 동시성 제어. Redis 없이 단순한 구조.
- **수량 확인**: `SELECT COUNT(*) FROM issued_coupon WHERE coupon_template_id = ?` vs CouponTemplate.maxQuantity

### D4. DB 공유
- **결정**: commerce-api와 commerce-streamer 동일 MySQL 사용
- **근거**: 설정 단순. streamer가 issued_coupon, coupon_issue_request 테이블 직접 접근 가능.

### D5. Outbox 저장 방식
- **결정**: Service에서 OutboxEventPort 호출 (명시적)
- **근거**: CLAUDE.md 아키텍처 규칙 준수 (Service 의존 대상 = Repository, Port, DomainService, EventPublisher). 도메인 변경과 같은 TX에서 outbox 저장 → At Least Once 보장.

### D6. 유저 행동 로깅 범위
- **결정**: VIEW + LIKE + ORDER + PAYMENT (CLICK 제외)
- **근거 (의도적 trade-off)**: 00-requirements.md 체크리스트에 "유저 행동(조회, 클릭, 좋아요, 주문 등)"이 포함되어 있으나, "등"으로 열거된 예시이며 CLICK 제외는 의도적 trade-off이다. VIEW가 상세 조회 시 자동 수집되므로 CLICK과 별도 구분의 실익이 없다 — 목록에서 상품 클릭 시 상세 페이지로 이동하면 VIEW가 자동 수집되므로 CLICK을 별도 추적하지 않는다.

### D7. VIEW 이벤트 발행 시점 및 Outbox 전략
- **결정**: 상품 상세 조회(GET /api/v1/products/{id})에서 자동 발행. Facade 메서드에서 `readOnly=true` 제거하여 같은 TX에서 outbox 저장.
- **근거**: 조회=VIEW가 자연스러움. outbox INSERT는 단순 INSERT라 독립적으로 실패할 가능성이 거의 없고, DB 커넥션 실패 시 조회 자체도 실패하므로 TX 분리의 실질적 이점 없음.
- **Outbox 전략**: ProductQueryFacade.getProduct()의 `@Transactional(readOnly = true)` → `@Transactional`으로 변경. 같은 TX 내에서 조회 + outbox INSERT. At Least Once 보장.
- **ViewEventCommandService 불필요**: 별도 쓰기 Service 없이 Facade 내에서 직접 처리. outbox 저장은 Service(ProductQueryService 또는 전용 Service)에서 OutboxEventPort 호출.

### D8. ReadModel 동기화 및 Batch 역할
- **결정**: ReadModel 실시간 반영은 ReadModelSyncConsumer(commerce-streamer)가 담당. commerce-batch는 reconciliation/backfill 전용.
- **근거**:
  - 요구사항의 "version/updated_at 기준 최신 이벤트만 반영"을 Kafka consumer 코드에서 직접 충족해야 함. batch로 우회하면 Consumer에서 배워야 할 핵심을 빠뜨림.
  - commerce-batch는 one-shot 실행 구조(job.name 기반, 실행 후 종료)로, 1분 간격 상시 동기화와는 성격이 맞지 않음.
  - **ReadModelSyncConsumer**: product-metrics-snapshots 토픽 구독. version 비교로 구버전 skip. ProductReadModel 업데이트 + 캐시 evict.
  - **batch**: 하루 1회 reconciliation (product_metrics vs product_read_model drift 보정). 안전망 역할.
  - **체감 지연 개선**: batch 간격이 빠짐. 상세 조회 ≈ MetricsCollector lag + ReadModelSync lag (상세 캐시 즉시 evict). 목록 조회 ≈ 같은 식 + 목록 캐시 TTL(3분).
  - **SoT 단일화 필수**: Step 2에서 기존 in-process ReadModel.likeCount 업데이트 경로 제거. product_read_model.like_count의 writer는 ReadModelSyncConsumer 하나로 통일

### D10. Consumer Group 분리
- **결정**: 4개 — metrics-collector, read-model-sync, coupon-issuer, user-action-logger
- **근거**: 요구사항의 "Consumer Group 분리를 통한 관심사별 처리". 각 관심사가 독립적으로 장애 격리/스케일링 가능.
  - `metrics-collector`: catalog-events, order-events 구독 → product_metrics upsert + snapshot 발행
  - `read-model-sync`: product-metrics-snapshots 구독 → ProductReadModel 반영 (version 비교 + 캐시 evict)
  - `coupon-issuer`: coupon-issue-requests 구독 → 선착순 쿠폰 발급
  - `user-action-logger`: catalog-events, order-events 구독 → 유저 행동 로그 중앙화 적재

### D11. 이벤트 테이블 분리 (event_handled vs event_log)
- **결정**: 분리
- **근거**: 멱등 테이블(event_handled)은 빠른 조회를 위해 최소한의 데이터만 보유 (event_id, consumer_group, handled_at). 로그 테이블(event_log)은 payload, 처리 결과 등 상세 이력 저장. 멱등 테이블 성능을 로그 데이터 증가로부터 보호.

### D12. Outbox Relay 실패 처리
- **결정**: FAILED 상태 + 재시도 (maxRetry 3회, 초과 시 DEAD)
- **근거**: 일시적 네트워크 장애는 재시도로 복구. 영구적 실패는 DEAD 상태로 전환하여 무한 루프 방지. 운영자가 DEAD 이벤트를 모니터링하여 수동 재처리.

### D13. Delta 원천 + Snapshot/Version 전파 2단 구조
- **결정**: 원천 이벤트는 delta + MetricsCollector가 집계 후 snapshot 이벤트 발행 + ReadModelSyncConsumer가 version 비교로 최신성 보장
- **근거**:
  - 카운터 원천 이벤트(PRODUCT_LIKED, PRODUCT_UNLIKED, PRODUCT_VIEWED, ORDER_PAID)는 "몇 건 발생했다"는 사실 이벤트이므로 delta가 자연스럽고 producer도 단순.
  - **delta 정합성 (1단 — MetricsCollectorConsumer)**: "최신 것만 반영"이 아니라 "모든 이벤트를 중복 없이 순서대로 반영". partition key + manual ack + event_handled로 보장.
  - **snapshot 전파 (2단 — ReadModelSyncConsumer)**: MetricsCollector가 delta 반영 후 `PRODUCT_METRICS_UPDATED(productId, likeCount, salesCount, viewCount, version, updatedAt)` snapshot 이벤트 발행. ReadModelSyncConsumer가 `incoming.version <= current.version`이면 skip → 요구사항의 "version/updated_at 기준 최신 이벤트만 반영"을 Kafka consumer 코드에서 직접 충족.
- **요구사항 충족 구조**: Consumer 필수 처리(manual Ack, event_handled 멱등, version 기준 최신성)를 모두 Kafka consumer 코드로 구현. batch가 아닌 consumer에서 직접 충족.

### D14. 기존 동기 쿠폰 발급 API 폐기
- **결정**: 기존 `POST /api/v1/coupons/{couponId}/issue` 폐기, 모든 쿠폰 발급을 Kafka 경로로 통합
- **근거**: 요구사항 "API는 발급 요청을 Kafka에 발행만 하고, Consumer가 실제 발급". 동기/비동기 경로가 공존하면 발급 수량 제어가 일관성 없어짐. 모든 발급을 단일 경로(Kafka)로 통일.
- **구현**: IssuedCouponCommandController 제거 (또는 @Deprecated). IssuedCouponCommandFacade.issueCoupon() 제거. 새 API `POST /api/v1/coupon-issue-requests`로 대체.

### D15. 쿠폰 polling requestId 반환
- **결정**: POST `/api/v1/coupon-issue-requests` 응답(202 Accepted)에 requestId를 포함하여 반환
- **근거**: 클라이언트가 polling에 사용할 requestId를 알아야 함. requestId는 클라이언트가 요청 시 전달하거나 서버가 생성하여 응답에 포함.
- **응답 형식**: `{ "requestId": "...", "status": "PENDING" }`

### D9. 구현 순서
- **결정**: Step 1 완료 → Step 2 순차 구현
- **근거**: 학습 단계별 진행. 점진적 변경으로 실패 범위 제한.

---

## Step 1 — ApplicationEvent 분리

### 1.1 이벤트 클래스

**`ordering/order/domain/event/OrderCreatedEvent.java`**
```java
/**
 * 주문 생성 완료 이벤트
 * @subscriber OrderEventListener - 장바구니 정리
 * @subscriber UserActionEventListener - 유저 행동 로깅 (ORDER)
 */
public record OrderCreatedEvent(
    Long orderId, Long userId, String requestId,
    List<Long> cartItemIds,
    List<OrderItemSnapshot> items,
    BigDecimal totalPrice, LocalDateTime occurredAt
) {
    public record OrderItemSnapshot(Long productId, Long quantity) {}
    public static OrderCreatedEvent from(Order order, List<Long> cartItemIds) { ... }
}
```

**`payment/payment/domain/event/OrderPaidEvent.java`**
```java
/**
 * 주문 결제 완료 이벤트
 * @subscriber UserActionEventListener - 유저 행동 로깅 (PAYMENT)
 */
public record OrderPaidEvent(
    Long orderId, Long userId, Long paymentId,
    List<OrderPaidItemSnapshot> items,
    BigDecimal totalPrice, LocalDateTime occurredAt
) {
    public record OrderPaidItemSnapshot(Long productId, Long quantity) {}
}
```
- **items 필드 필요 근거**: MetricsCollectorConsumer가 ORDER_PAID에서 `sales_count += quantity`를 productId별로 집계하려면 주문 품목 정보가 필수.
- **items 조회 — `PaymentOrderReader` port 확장**:
  ```java
  // PaymentOrderReader (port interface — 신규 메서드 추가)
  public interface PaymentOrderReader {
      PaymentOrderInfo findOrderForPayment(Long orderId, Long userId);  // 기존 유지
      List<PaymentOrderItemInfo> findOrderItems(Long orderId);          // 신규: ORDER_PAID payload용
  }
  record PaymentOrderItemInfo(Long productId, Long quantity) {}
  ```
  - `applyPgResult()`에서 결제 성공 시 `paymentOrderReader.findOrderItems(payment.getOrderId())` 호출
  - ACL 구현체(`PaymentOrderReaderImpl`)는 `OrderQueryFacade`를 통해 주문 items 조회
  - 결제 실패 시에는 호출 안 함 (불필요한 조회 방지)

**`engagement/productlike/domain/event/ProductLikedEvent.java`**
```java
/**
 * 상품 좋아요 생성 이벤트
 * @subscriber ProductLikeEventListener - likeCount 증가 (eventual consistency)
 * @subscriber UserActionEventListener - 유저 행동 로깅 (LIKE)
 */
public record ProductLikedEvent(
    Long productLikeId, Long userId, Long productId, LocalDateTime occurredAt
) { ... }
```

**`engagement/productlike/domain/event/ProductUnlikedEvent.java`**
```java
/**
 * 상품 좋아요 삭제 이벤트
 * @subscriber ProductLikeEventListener - likeCount 감소 (eventual consistency)
 */
public record ProductUnlikedEvent(Long userId, Long productId, LocalDateTime occurredAt) { ... }
```

**`catalog/product/domain/event/ProductViewedEvent.java`**
```java
/**
 * 상품 상세 조회 이벤트 (userId nullable — 비로그인 조회 포함)
 * @subscriber UserActionEventListener - 유저 행동 로깅 (VIEW) — Step 2 이후 제거
 */
public record ProductViewedEvent(Long userId, Long productId, LocalDateTime occurredAt) { ... }
// userId == null: 비로그인 조회 (view_count 집계 대상, 로그는 익명 조회로 적재)
```

### 1.2 코드 변경 (Publisher)

**OrderCommandService.java** (`:52-73`)
- `ApplicationEventPublisher` 주입
- 장바구니 삭제(`orderCartItemCleaner.deleteCartItems`) 제거
- 주문 저장 후 `OrderCreatedEvent` 발행
- 인라인 주석: `// → [OrderEventListener] 장바구니 정리 + [UserActionEventListener] ORDER 로깅`

**PaymentCommandService.java** (`:265-279` applyPgResult)
- `ApplicationEventPublisher` 주입
- `payment.succeed()` + `markOrderPaid()` 후 `OrderPaidEvent` 발행
- 인라인 주석: `// → [UserActionEventListener] PAYMENT 로깅`

**ProductLikeCommandFacade.java** (`:31-47`, `:52-59`)
- `ApplicationEventPublisher` 주입
- `createLike`: `productLikeCommandService.increaseLikeCount(targetId)` 제거 → `ProductLikedEvent` 발행
- `deleteLike`: `productLikeCommandService.decreaseLikeCount(targetId)` 제거 → `ProductUnlikedEvent` 발행
- 인라인 주석: `// → [ProductLikeEventListener] likeCount 증가/감소 (eventual consistency)`

**ProductQueryFacade** (상품 상세 조회 메서드)
- `ApplicationEventPublisher` 주입
- `@Transactional(readOnly = true)` → `@Transactional`으로 변경 (D7: outbox 쓰기를 위해 readOnly 제거)
- Facade 시그니처 변경: `getProduct(Long id)` → `getProduct(Long id, Long userId)` (userId nullable — 비로그인 시 null)
- Controller: `AuthenticationResolver.resolveOptional(loginId, password)` 호출 → 헤더 있으면 userId, 없으면 null → Facade에 전달
  - `resolveOptional` 구현 로직:
    ```java
    public Long resolveOptional(String loginId, String password) {
        // 둘 다 null/blank → 비로그인 (null 반환, 예외 없음)
        if ((loginId == null || loginId.isBlank()) && (password == null || password.isBlank())) {
            return null;
        }
        // 하나라도 있으면 → 기존과 동일하게 필수 검증 + 인증
        HeaderValidator.validate(loginId, password);  // 한쪽만 있으면 여기서 예외
        return userQueryFacade.authenticateAndGetUserId(loginId, password);
    }
    ```
  - 기존 `resolve()`(필수 인증)는 변경 없음. 공존.
  - ⚠️ **구현 주의**: 공개 상품 조회가 optional auth 추가 후에도 반드시 인증 없이 동작해야 함.
- **모든 상세 조회에서** `ProductViewedEvent` 발행 (userId nullable). 비로그인 조회도 view_count 집계 대상.
- 인라인 주석: `// → [UserActionEventListener] VIEW 로깅 (Step 1), Step 2 이후 제거`
- **Step 2 Outbox**: 같은 TX 내에서 outbox 저장. `outboxEventPort.save("PRODUCT", productId, "PRODUCT_VIEWED", "catalog-events", productId, payload)` — payload에 **userId(nullable), productId, occurredAt** 포함.

### 1.3 이벤트 리스너

**`ordering/order/interfaces/event/OrderEventListener.java`**
```java
@Component
public class OrderEventListener {
    // 1. 장바구니 정리 (주문 커밋 후 비동기)
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handleCartCleanup(OrderCreatedEvent event) {
        orderCartItemCleaner.deleteCartItems(event.userId(), event.cartItemIds());
    }
}
```

**`engagement/productlike/interfaces/event/ProductLikeEventListener.java`**
```java
@Component
public class ProductLikeEventListener {
    // 1. likeCount 증가 (eventual consistency)
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handleLikeCountIncrease(ProductLikedEvent event) {
        productLikeCommandService.increaseLikeCount(event.productId());
    }
    // 2. likeCount 감소
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handleLikeCountDecrease(ProductUnlikedEvent event) {
        productLikeCommandService.decreaseLikeCount(event.productId());
    }
}
```

**`support/common/event/UserActionEventListener.java`**
```java
@Component
@Slf4j
public class UserActionEventListener {
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("[UserAction] userId={} action=ORDER target=ORDER:{}", event.userId(), event.orderId());
    }
    // ... PAYMENT, LIKE, VIEW 각각 핸들러
}
```
> **Step 2 이후 UserActionEventListener 제거**: Kafka 중앙 로그(UserActionLogConsumer)가 event_log에 상세 이력을 적재하므로 in-process SLF4J 로깅은 불필요. VIEW 전량 수집 시 이중 로깅의 양도 많아지므로 제거.

### 1.4 @Async 설정

**`support/config/AsyncConfig.java`** (신규)
```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    // ThreadPoolTaskExecutor: core=5, max=20, queue=100, prefix="event-async-"
}
```

### 1.5 트랜잭션 경계 변화

| 변경 전 | 변경 후 |
|--------|--------|
| OrderCommandService TX: 재고+쿠폰+저장+장바구니삭제 | OrderCommandService TX: 재고+쿠폰+저장만. 장바구니삭제는 AFTER_COMMIT |
| ProductLikeCommandFacade TX: 좋아요+likeCount동기 | Facade TX: 좋아요만. likeCount는 AFTER_COMMIT |
| 결제완료 후 즉시 반환 | 결제완료 TX 커밋 후 AFTER_COMMIT으로 로깅 |

---

## Step 2 — Kafka Outbox + Streamer Consumer

### 2.1 Outbox 인프라

**테이블: 앱별 outbox 물리 분리** (스키마 동일, 테이블명만 다름)

`outbox_event_api` (commerce-api용 — 도메인 이벤트):
```sql
CREATE TABLE outbox_event_api (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,      -- ORDER, PRODUCT, COUPON
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,         -- ORDER_CREATED, PRODUCT_LIKED, ...
    topic VARCHAR(100) NOT NULL,
    partition_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,                    -- JSON
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, PUBLISHED, FAILED, DEAD
    retry_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6),
    INDEX idx_outbox_status_created (status, created_at)
);
```

`outbox_event_streamer` (commerce-streamer용 — snapshot 이벤트):
- 스키마 동일. `@Table(name = "outbox_event_streamer")`만 다름.

**공유 모듈**: `support/common/outbox/` (로직 공유)
```
support/common/outbox/
├── application/
│   ├── port/OutboxEventPort.java             — 인터페이스 (save, findRetryable, markPublished, markFailed, markDead)
│   ├── dto/OutboxEventDto.java               — 조회 결과 DTO (app layer가 다루는 데이터)
│   ├── enums/OutboxStatus.java               — PENDING, PUBLISHED, FAILED, DEAD
│   └── scheduler/OutboxRelayScheduler.java   — 스케줄링 + 재시도 정책 + 오케스트레이션
```

**앱별 인프라** (boilerplate — Entity/JpaRepo/PortImpl만 각 앱에 위치):
```
commerce-api:   infrastructure/outbox/ → OutboxEventApiEntity(@Table="outbox_event_api"), JpaRepo, PortImpl
commerce-streamer: infrastructure/outbox/ → OutboxEventStreamerEntity(@Table="outbox_event_streamer"), JpaRepo, PortImpl
```

> **Outbox는 persistence model만 사용 (domain model 없음)**
> - Outbox는 비즈니스 도메인이 아닌 인프라 메커니즘 (메시지 전달 보장).
> - 상태 전이 **결정**(재시도 정책, DEAD 판정)은 app layer(Scheduler)에서 수행.
> - infra layer는 단순 CRUD만 담당. Entity에 비즈니스 메서드 없음.
> - 재시도 정책(`MAX_RETRY = 3`)은 도메인 비즈니스 규칙이 아닌 운영 정책이므로 app layer에 위치.
>
> **Outbox 테이블을 앱별로 물리 분리하는 이유**
> - 같은 테이블에 두 앱이 INSERT + relay SELECT하면, 이벤트량 증가 시 동시성 부하 우려 (AUTO_INCREMENT gap lock, relay 경합).
> - 앱별 테이블 분리로: relay가 자기 테이블만 접근하여 경합 없음. 모니터링/cleanup 정책도 독립 관리 가능.
> - 코드 공유: 로직(Port, DTO, Scheduler)은 공유 모듈에, 인프라 boilerplate(Entity, JpaRepo, PortImpl — ~50줄)만 앱별로 존재. 실질적 중복 없음.
> - **DI 연결**: 각 앱에 `OutboxEventPortImpl`이 하나만 존재하므로, 공유 모듈의 `OutboxRelayScheduler`에 자동 주입됨. 별도 설정 불필요.

### 2.2 OutboxEventPort

```java
public interface OutboxEventPort {
    // 저장 (Service TX 내에서 호출)
    void save(String aggregateType, String aggregateId, String eventType,
              String topic, String partitionKey, String payload);
    // 조회 (Scheduler에서 호출)
    List<OutboxEventDto> findRetryableEvents(int limit);
    // 상태 변경 (Scheduler에서 결정 후 호출)
    void markPublished(Long id);
    void markFailed(Long id);
    void markDead(Long id);
}
```

### 2.3 Kafka Payload 구조 (이벤트별)

> 발행자(Producer)가 payload 구조를 소유. 소비자(Consumer)는 JSON을 직접 파싱. 앱 간 컴파일 의존성 없음.

**위치**: 각 payload record는 해당 도메인의 `application/dto/out/` 하위에 배치.

```java
// ordering/order/application/dto/out/
record OrderCreatedPayload(
    Long orderId, Long userId, String requestId,
    List<OrderItemPayload> items,
    BigDecimal totalPrice, LocalDateTime occurredAt
) {}

record OrderPaidPayload(
    Long orderId, Long userId, Long paymentId,
    List<OrderItemPayload> items,       // sales_count 집계용
    BigDecimal totalPrice, LocalDateTime occurredAt
) {}

record OrderItemPayload(Long productId, Long quantity) {}

// engagement/productlike/application/dto/out/
record ProductLikedPayload(Long productLikeId, Long userId, Long productId, LocalDateTime occurredAt) {}
record ProductUnlikedPayload(Long userId, Long productId, LocalDateTime occurredAt) {}

// catalog/product/application/dto/out/
record ProductViewedPayload(Long userId, Long productId, LocalDateTime occurredAt) {}
// userId nullable — 비로그인 시 null

// coupon/issuedcoupon/application/dto/out/
record CouponIssueRequestedPayload(String requestId, Long userId, Long couponTemplateId, LocalDateTime occurredAt) {}

// commerce-streamer metrics/ (snapshot — streamer가 소유)
record ProductMetricsUpdatedPayload(
    Long productId, Long likeCount, Long salesCount, Long viewCount,
    Long version, LocalDateTime updatedAt
) {}
```

### 2.4 Outbox 발행 연동 (Service에서 호출)

**OrderCommandService** — createOrder 내:
```java
outboxEventPort.save("ORDER", orderId, "ORDER_CREATED",
    "order-events", orderId, toJson(OrderCreatedPayload.from(order, cartItems)));
```

**PaymentCommandService** — applyPgResult 내 (SUCCESS일 때):
```java
outboxEventPort.save("ORDER", orderId, "ORDER_PAID",
    "order-events", orderId, toJson(OrderPaidPayload.from(payment, orderItems)));
```

**ProductLikeCommandService** — createLike 내:
```java
outboxEventPort.save("PRODUCT", productId, "PRODUCT_LIKED",
    "catalog-events", productId, toJson(ProductLikedPayload.from(productLike)));
```

**ProductLikeCommandService** — deleteLike 내:
```java
outboxEventPort.save("PRODUCT", productId, "PRODUCT_UNLIKED",
    "catalog-events", productId, toJson(new ProductUnlikedPayload(userId, productId, LocalDateTime.now())));
```

**ProductQueryFacade (또는 해당 Service)** — 상품 상세 조회 시 같은 TX에서:
```java
outboxEventPort.save("PRODUCT", productId, "PRODUCT_VIEWED",
    "catalog-events", productId, toJson(new ProductViewedPayload(userId, productId, LocalDateTime.now())));
```

**CouponIssueRequestCommandService** — 쿠폰 발급 요청 시:
```java
outboxEventPort.save("COUPON", couponTemplateId, "COUPON_ISSUE_REQUESTED",
    "coupon-issue-requests", couponTemplateId, toJson(CouponIssueRequestedPayload.from(request)));
```

### 2.5 Outbox Relay Scheduler (D12 실패 전략 반영)

```java
@Component
public class OutboxRelayScheduler {
    private static final int MAX_RETRY = 3;  // 운영 정책 (도메인 규칙 아님)

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        // 조회: PENDING + FAILED(retryCount < 3) — Port가 DTO로 반환
        List<OutboxEventDto> events = outboxEventPort.findRetryableEvents(100);

        for (OutboxEventDto event : events) {
            try {
                // Kafka 발행 (infra I/O)
                kafkaTemplate.send(event.topic(), event.partitionKey(), event.payload())
                    .get(5, SECONDS);
                // 상태 전이 결정 (app) → 실행 위임 (infra)
                outboxEventPort.markPublished(event.id());
            } catch (Exception e) {
                // 재시도 정책 판단 (app layer 운영 정책)
                if (event.retryCount() + 1 >= MAX_RETRY) {
                    outboxEventPort.markDead(event.id());
                    log.error("[Outbox] DEAD eventId={}", event.id(), e);
                } else {
                    outboxEventPort.markFailed(event.id());
                    log.warn("[Outbox] FAILED eventId={} retry={}", event.id(), event.retryCount() + 1, e);
                }
            }
        }
    }
}
```

### 2.6 Kafka 설정 변경

**설정 분리 전략: 코드 공유, 설정값 분리**

```
modules/kafka/
├── KafkaConfig.java         — 공유 (factory, template 등 코드 로직)
├── kafka.yml                — 공통 인프라만 (bootstrap-servers, 환경별 프로필, serializer/deserializer)

apps/commerce-api/application.yml       — producer 설정 (acks, idempotence)
apps/commerce-streamer/application.yml  — consumer 설정 (group-id, batch) + producer 설정 (outbox relay용)
```

- 코드(KafkaConfig): factory, template 등 로직은 공유 모듈에서 한 곳 관리
- 인프라(kafka.yml): bootstrap-servers, serializer 등 공통 인프라는 공유
- 동작 설정: producer acks, consumer group 등 앱별 동작은 각 앱 application.yml에서 독립 관리
- 근거: api는 producer 위주, streamer는 consumer 위주로 튜닝 방향이 다름. 공유하면 한쪽 변경이 다른 쪽에 영향.

**kafka.yml** (공통 인프라만 — producer/consumer 동작 설정 제거):
```yaml
spring:
  kafka:
    bootstrap-servers: ${BOOTSTRAP_SERVERS}
    client-id: ${spring.application.name}
    properties:
      spring.json.add.type.headers: false
      request.timeout.ms: 20000
      retry.backoff.ms: 500
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.ByteArrayDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    listener:
      ack-mode: manual
```

**commerce-api application.yml** (producer 전용):
```yaml
spring.kafka.producer:
  acks: all                          # 모든 ISR이 저장해야 OK (메시지 유실 방지)
  properties:
    enable.idempotence: true         # Producer ID + 시퀀스로 중복 저장 방지
  # retries 제거: idempotence=true면 자동 무한 재시도
```

**commerce-streamer application.yml** (consumer + producer):
```yaml
spring.kafka:
  producer:
    acks: all
    properties:
      enable.idempotence: true       # outbox relay용 producer도 동일 설정
  consumer:
    properties:
      enable-auto-commit: false      # 수동 커밋 (manual ack)
  # consumer group-id는 @KafkaListener(groupId = "...")에서 지정
```

**토픽 생성 — `@Bean NewTopic(...)` 방식**:
- 앱 시작 시 `KafkaAdmin`이 자동 생성. 이미 존재하면 무시.
- 토픽 설정 클래스: `support/config/KafkaTopicConfig.java` (commerce-api, commerce-streamer 각각)

| 토픽 | 파티션 | Replication (local/test) | Replication (dev/qa/prd) | 근거 |
|------|--------|-------------------------|--------------------------|------|
| `catalog-events` | 3 | 1 | 3 | 상품별 분산. concurrency=3과 맞춤 |
| `order-events` | 3 | 1 | 3 | 주문별 분산 |
| `coupon-issue-requests` | 1 | 1 | 3 | 단일 파티션 순차 처리 (D3) |
| `product-metrics-snapshots` | 3 | 1 | 3 | 상품별 분산 |
| `*.DLT` (DLQ 토픽) | 1 | 1 | 3 | DLT는 처리량 낮음, 1파티션 충분 |

```java
@Configuration
public class KafkaTopicConfig {
    @Bean public NewTopic catalogEvents() { return TopicBuilder.name("catalog-events").partitions(3).replicas(replicationFactor).build(); }
    @Bean public NewTopic orderEvents() { return TopicBuilder.name("order-events").partitions(3).replicas(replicationFactor).build(); }
    @Bean public NewTopic couponIssueRequests() { return TopicBuilder.name("coupon-issue-requests").partitions(1).replicas(replicationFactor).build(); }
    @Bean public NewTopic productMetricsSnapshots() { return TopicBuilder.name("product-metrics-snapshots").partitions(3).replicas(replicationFactor).build(); }
    // replicationFactor: @Value("${kafka.topic.replication-factor:1}") — 환경별 yml에서 주입
}
```

**KafkaConfig — Listener Container Factory 2개**:

| Factory | concurrency | 배치 | 대상 컨슈머 그룹 |
|---------|-------------|------|-----------------|
| `BATCH_LISTENER_DEFAULT` (기존) | 3 | true | metrics-collector, user-action-logger, read-model-sync |
| `SINGLE_LISTENER_COUPON` (신규) | 1 | true | coupon-issuer (단일 파티션 순차 처리, D3) |

```java
// KafkaConfig.java에 추가
public static final String SINGLE_LISTENER_COUPON = "SINGLE_LISTENER_COUPON";

@Bean(name = SINGLE_LISTENER_COUPON)
public ConcurrentKafkaListenerContainerFactory<Object, Object> couponListenerContainerFactory(...) {
    // BATCH_LISTENER_DEFAULT와 동일하되 concurrency=1
    factory.setConcurrency(1);  // 단일 파티션 순차 처리
    factory.setBatchListener(true);
    factory.getContainerProperties().setAckMode(AckMode.MANUAL);
    return factory;
}
```

**commerce-api build.gradle.kts** — 의존성 추가:
```kotlin
implementation(project(":modules:kafka"))
testImplementation(testFixtures(project(":modules:kafka")))
```

### 2.7 토픽 설계

| 토픽 | Partition Key | 이벤트 | 성격 |
|------|---------------|--------|------|
| `catalog-events` | productId | PRODUCT_LIKED, PRODUCT_UNLIKED, PRODUCT_VIEWED | delta |
| `order-events` | orderId | ORDER_CREATED, ORDER_PAID | delta |
| `coupon-issue-requests` | couponTemplateId | COUPON_ISSUE_REQUESTED | command |
| `product-metrics-snapshots` | productId | PRODUCT_METRICS_UPDATED | snapshot |

### 2.8 Commerce-Streamer Consumer

**테이블: `product_metrics`**
```sql
CREATE TABLE product_metrics (
    product_id BIGINT PRIMARY KEY,
    like_count BIGINT NOT NULL DEFAULT 0,
    sales_count BIGINT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL
);
```

**테이블: `event_handled`** (멱등 처리 — 최소한의 데이터로 빠른 조회)
```sql
CREATE TABLE event_handled (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    handled_at DATETIME(6) NOT NULL,
    UNIQUE INDEX idx_event_handled_event_group (event_id, consumer_group)
);
```

**테이블: `event_log`** (상세 이력 — payload, 처리 결과 기록)
```sql
CREATE TABLE event_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    payload TEXT,
    status VARCHAR(20) NOT NULL,             -- SUCCESS, FAILED
    error_message VARCHAR(500),
    handled_at DATETIME(6) NOT NULL,
    INDEX idx_event_log_event_id (event_id),
    INDEX idx_event_log_type_created (event_type, handled_at)
);
```

> **왜 event_handled와 event_log를 분리하는가?**
> - event_handled: 멱등 판정에만 사용. PK 기반 존재 여부 확인이므로 row 수가 적고 조회가 빨라야 함.
> - event_log: 디버깅/운영 이력용. payload와 에러 메시지 등 데이터가 크고 조회 빈도가 낮음.
> - 같은 테이블에 두면 멱등 체크 쿼리가 대용량 로그 데이터에 영향받아 성능 저하.

**Consumer 구조** (commerce-streamer):
```
com.loopers
├── metrics/
│   ├── interfaces/consumer/MetricsCollectorConsumer.java
│   ├── application/service/ProductMetricsService.java
│   ├── infrastructure/entity/ProductMetricsEntity.java
│   ├── infrastructure/jpa/ProductMetricsJpaRepository.java
│   └── infrastructure/entity/EventHandledEntity.java
├── logging/
│   ├── interfaces/consumer/UserActionLogConsumer.java
│   ├── application/service/UserActionLogService.java
│   └── infrastructure/entity/EventLogEntity.java
├── readmodel/
│   ├── interfaces/consumer/ReadModelSyncConsumer.java
│   └── application/service/ReadModelSyncService.java
├── coupon/
│   ├── interfaces/consumer/CouponIssueConsumer.java
│   └── application/service/CouponIssueProcessorService.java
└── support/
    ├── event/KafkaEventEnvelope.java
    ├── idempotency/EventIdempotencyService.java
    └── config/KafkaTopicConfig.java
```

**Consumer Group 4개 분리** (D10 확장):
- **`metrics-collector`**: catalog-events, order-events 구독 → product_metrics upsert + snapshot 이벤트 발행
- **`read-model-sync`**: product-metrics-snapshots 구독 → ProductReadModel 반영 (version 비교 + 캐시 evict)
- **`user-action-logger`**: catalog-events, order-events 구독 → event_log 적재 (중앙화된 유저 행동 로그)
- **`coupon-issuer`**: coupon-issue-requests 구독 → 선착순 쿠폰 발급

**MetricsCollectorConsumer**: Consumer group `metrics-collector`
- `catalog-events`, `order-events` 구독
- **배치 처리** (KafkaConfig.BATCH_LISTENER 활용 — MAX_POLLING_SIZE 3000, concurrency 3):
```java
@KafkaListener(topics = {"catalog-events", "order-events"},
    groupId = "metrics-collector",
    containerFactory = KafkaConfig.BATCH_LISTENER)
public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
    // 1. event_handled 일괄 조회로 이미 처리된 이벤트 필터링
    // 2. 배치 내 같은 productId에 대한 delta 합산
    //    예: productId=1에 LIKED 3건, UNLIKED 1건 → like_count += 2
    // --- 동일 DB TX 시작 (@Transactional) ---
    // 3. 합산 결과로 product_metrics bulk upsert
    //    (version = version + 1, updated_at = now()) — 갱신마다 +1, delta 합산값과 무관
    // 4. event_handled 일괄 저장
    // 5. 변경된 productId에 대해 PRODUCT_METRICS_UPDATED snapshot outbox 저장
    //    (같은 TX — At Least Once 보장)
    // --- TX commit ---
    // 6. ack.acknowledge()
    //
    // ⚠️ 3+4+5는 반드시 동일 DB TX로 묶어야 함.
    //   - 3+4 분리: delta 이중 적용 위험
    //   - 5 분리: snapshot 발행 누락 시 ReadModel이 다음 delta까지 stale
    // ⚠️ snapshot Kafka 발행은 OutboxRelayScheduler(streamer)가 별도 수행.
}
```
- 이벤트 타입별 delta:
  - PRODUCT_LIKED → `like_count += 1`
  - PRODUCT_UNLIKED → `like_count -= 1`
  - PRODUCT_VIEWED → `view_count += 1`
  - ORDER_PAID → 각 주문 항목의 `sales_count += quantity` (payload에 items 포함)

**UserActionLogConsumer**: Consumer group `user-action-logger`
- `catalog-events`, `order-events` 구독
- **배치 처리** (KafkaConfig.BATCH_LISTENER 활용):
- 동일 DB TX에서 event_log bulk insert + event_handled 저장 → commit 후 ack
  - (MetricsCollector와 동일 패턴 — retry 시 로그 중복 방지)
- event_handled 기반 멱등 처리
- Manual Ack

### 2.8.1 2단 Consumer 구조: Delta → Snapshot → ReadModel (D13 반영)

> 카운터 원천 이벤트는 delta이므로 event_handled + partition ordering으로 정합성을 보장한다. 요구사항의 "version/updated_at 기준 최신 이벤트만 반영"은 snapshot consumer(ReadModelSyncConsumer)에서 Kafka consumer 코드로 직접 충족한다.

**1단 — MetricsCollectorConsumer (delta 처리)**:
- catalog-events, order-events 구독 (consumer group: `metrics-collector`)
- delta 이벤트를 event_handled + partition ordering으로 중복 없이 순서대로 반영
- 같은 TX에서 product_metrics upsert + event_handled 저장 + snapshot outbox 저장 (At Least Once 보장)
- OutboxRelayScheduler(streamer)가 outbox → product-metrics-snapshots 토픽으로 발행

**PRODUCT_METRICS_UPDATED snapshot 이벤트**:
```java
public record ProductMetricsUpdatedPayload(
    Long productId,
    Long likeCount, Long salesCount, Long viewCount,
    Long version, LocalDateTime updatedAt
) {}
```
- 토픽: `product-metrics-snapshots` (key = productId)
- absolute-state 이벤트 → 수신측에서 version 비교로 구버전 skip 가능

**2단 — ReadModelSyncConsumer (snapshot → ReadModel 반영)**:
- `product-metrics-snapshots` 구독 (consumer group: `read-model-sync`)
- **요구사항 literal 충족**:
  - manual Ack
  - event_handled 기반 멱등 처리
  - `incoming.version <= current ProductReadModel의 metrics_version` → skip (version 기준 최신 이벤트만 반영)
- 같은 TX에서 ProductReadModel update + event_handled 저장
- commit 후 캐시 evict (상세 캐시)
- commit 후 ack

> **ProductReadModel에 `metrics_version` 컬럼 추가**: ReadModelSyncConsumer가 version 비교에 사용. 같은 DB이므로 JOIN 불필요 — snapshot payload의 version과 ReadModel의 metrics_version을 비교.

**KafkaEventEnvelope** (공통 JSON 포맷):
```java
public record KafkaEventEnvelope(
    String eventId,       // Outbox ID (멱등 키)
    String eventType,
    String aggregateType,
    String aggregateId,
    long version,         // outbox_event.id (단조 증가, 로깅/디버깅용)
    String data,          // 실제 페이로드 JSON
    LocalDateTime occurredAt
) {}
```

### 2.9 DLQ 설정

**KafkaConfig에 DLQ ErrorHandler 추가**:
- **ExponentialBackOff**, 3회 재시도 후 `{topic}.DLT` 토픽으로 이동
  - 재시도 간격: 1초 → 2초 → 4초 (exponential). 대용량 트래픽 장애 시 고정 간격보다 부하 완화.
- DLT 토픽: `catalog-events.DLT`, `order-events.DLT`, `coupon-issue-requests.DLT`, `product-metrics-snapshots.DLT`

```java
// KafkaConfig.java에 추가
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
        (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
    ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);  // 초기 1초, 2배씩
    backOff.setMaxElapsedTime(7000L);  // 최대 7초 (1 + 2 + 4)
    return new DefaultErrorHandler(recoverer, backOff);
}
```

### 2.10 Step 2에서 ProductLikeEventListener 변경

- Step 1에서 추가한 `handleLikeCountIncrease/Decrease` 리스너 **제거**
- likeCount 업데이트는 이제 Kafka → MetricsCollector → product_metrics → snapshot outbox → ReadModelSyncConsumer → ProductReadModel 경로로만 처리
- commerce-batch는 reconciliation 안전망 (하루 1회 drift 보정)

---

## Step 3 — 선착순 쿠폰 발급

### 3.1 CouponTemplate 확장

**CouponTemplate 도메인 모델** — `maxQuantity` 필드 추가:
- `create()`, `reconstruct()` 팩토리 메서드에 `maxQuantity` 파라미터 추가
- `maxQuantity == null` → 무제한 발급

**CouponTemplateEntity** — `max_quantity` 컬럼 추가

**Admin DTO 변경**: `AdminCouponTemplateCreateInDto`, `AdminCouponTemplateCreateRequest`에 `maxQuantity` 필드

### 3.2 CouponIssueRequest 도메인

**테이블: `coupon_issue_request`**
```sql
CREATE TABLE coupon_issue_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    coupon_template_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reject_reason VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE INDEX idx_cir_request_id (request_id),
    INDEX idx_cir_user_template (user_id, coupon_template_id),
    INDEX idx_cir_status_created (status, created_at)
);
```

**위치**: `coupon/issuedcoupon/` 하위에 통합 (IssuedCoupon BC와 밀접)

**도메인 모델**: `CouponIssueRequest.java`
- `create(requestId, userId, couponTemplateId)` → status = PENDING
- `markIssued()` / `reject(reason)`

**Enum**: `CouponIssueRequestStatus` — PENDING, ISSUED, REJECTED

### 3.3 API (commerce-api)

**기존 동기 발급 API 폐기 (D14)**:
- `POST /api/v1/coupons/{couponId}/issue` (IssuedCouponCommandController) → 제거
- `IssuedCouponCommandFacade.issueCoupon()` → 제거
- 모든 쿠폰 발급을 Kafka 경로로 통합

**POST `/api/v1/coupon-issue-requests`** → 202 Accepted
- Controller → Facade → Service
- Service TX: CouponIssueRequest 저장 (PENDING) + Outbox 저장 (coupon-issue-requests 토픽)
- 멱등: requestId 기반 중복 검사 (이미 존재하면 기존 반환)
- **응답에 requestId 포함** (D15): `{ "requestId": "...", "status": "PENDING" }` — 클라이언트가 polling에 사용
- requestId: 클라이언트가 Request body에 전달 (UUID). 미전달 시 서버가 생성.

**GET `/api/v1/coupon-issue-requests/{requestId}`** → 200 OK
- 결과 polling (PENDING / ISSUED / REJECTED)
- userId 검증 (본인 요청만 조회)

### 3.4 Consumer (commerce-streamer)

**CouponIssueConsumer**: Consumer group `coupon-issuer`
- `coupon-issue-requests` 토픽 구독
- 단일 파티션 순차 처리로 동시성 제어

**CouponIssueProcessorService**:
```java
@Transactional
public void processIssueRequest(KafkaEventEnvelope envelope) {
    // 1. 멱등 검사 (event_handled)
    // 2. 중복 발급 검사 (userId + couponTemplateId — DB 조회)
    // 3. 수량 확인: COUNT(issued_coupon WHERE template_id = ?) vs CouponTemplate.maxQuantity
    // 4. 초과 시 → coupon_issue_request REJECTED ("COUPON_SOLD_OUT")
    // 5. 미초과 시 → IssuedCoupon INSERT + coupon_issue_request ISSUED
    // 6. event_handled 기록
}
```

### 3.5 Streamer 경량 엔티티 정의

commerce-streamer는 같은 DB를 공유하지만, 엔티티는 streamer에 독립 정의한다. 각 역할에 필요한 컬럼만 매핑하는 경량 엔티티.

> 원칙: Outbox 테이블 분리와 동일 — 코드 독립, 테이블 공유. commerce-api 엔티티에 의존하지 않음.

**쿠폰 발급용** (`coupon/infrastructure/entity/`):

```java
// 쿠폰 발급 INSERT용 — 필요 컬럼만 매핑
@Entity @Table(name = "issued_coupon")
public class StreamerIssuedCouponEntity {
    @Id @GeneratedValue(strategy = IDENTITY) private Long id;
    private Long userId;
    private Long couponTemplateId;
    private String status;          // "AVAILABLE"
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}

// maxQuantity 조회용 — 읽기 전용
@Entity @Table(name = "coupon_template")
public class StreamerCouponTemplateEntity {
    @Id private Long id;
    private Integer maxQuantity;    // null = 무제한
}

// 발급 요청 상태 업데이트용
@Entity @Table(name = "coupon_issue_request")
public class StreamerCouponIssueRequestEntity {
    @Id @GeneratedValue(strategy = IDENTITY) private Long id;
    private String requestId;
    private Long userId;
    private Long couponTemplateId;
    private String status;          // PENDING → ISSUED / REJECTED
    private String rejectReason;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
```

**Read Model 동기화용** (`readmodel/infrastructure/entity/`):

```java
// ReadModelSyncConsumer가 like_count + metrics_version만 UPDATE
// sales_count, view_count는 현재 API에서 노출하지 않으므로 ReadModel에 미포함
// → product_metrics에만 집계 (분석용). 향후 API 노출 시 ReadModel 컬럼 추가.
@Entity @Table(name = "product_read_model")
public class StreamerProductReadModelEntity {
    @Id private Long id;            // product_id
    private Long likeCount;
    private Long metricsVersion;    // snapshot version 비교용
}
```

---

## Commerce-Batch: Reconciliation / Backfill 전용

**위치**: `apps/commerce-batch/`
**역할 변경**: ReadModel 실시간 반영은 ReadModelSyncConsumer(commerce-streamer)가 담당. batch는 reconciliation/backfill 안전망으로 역할 축소.

### 전제
- product_metrics.like_count가 SoT
- product_read_model.like_count는 API 조회용 projection
- product_read_model.like_count의 주 writer는 ReadModelSyncConsumer (snapshot consumer)
- 기존 in-process ReadModel.likeCount 업데이트 경로는 Step 2에서 제거

### 체감 지연 구조 (ReadModelSyncConsumer 기준, 엔드포인트별 상이)
```
상세 조회 체감 지연 ≈ MetricsCollector lag + ReadModelSync lag
목록 조회 체감 지연 ≈ MetricsCollector lag + ReadModelSync lag + 목록 캐시 TTL (3분)
```
- ReadModelSyncConsumer가 상세 캐시를 즉시 evict하므로, 상세 조회는 캐시 TTL이 지연에 안 더해짐.
- 목록 캐시(ID 리스트)는 별도 evict 없이 TTL 자연 만료에 의존하므로 최대 3분 추가 stale.

### Job: `ProductMetricsReconciliationJob` (안전망)
- **역할**: product_metrics와 product_read_model의 drift를 보정 (데이터 불일치 감지 + 보정)
- **주기**: 하루 1회 (또는 수동 트리거)
- **방식**: product_metrics 전체와 product_read_model 전체를 비교. 불일치 발견 시 ReadModel 보정 + 로그 기록.
- **용도**: consumer 장애, 이벤트 유실, 버그 등으로 인한 누적 drift 보정. 실시간 동기화가 아닌 안전망.

### 왜 batch가 주 경로가 아닌가
- 요구사항의 "version/updated_at 기준 최신 이벤트만 반영"을 Kafka consumer 코드에서 직접 충족해야 함 (D13).
- commerce-batch는 one-shot 실행 구조(job.name 기반, 실행 후 종료)로, 상시 동기화와 성격이 맞지 않음.
- ReadModelSyncConsumer가 snapshot 이벤트를 소비하며 version 비교 + manual ack + event_handled 멱등을 모두 consumer에서 구현.

### 제거 대상 (Step 2)
- `ProductCommandService.increaseLikeCount()` → 호출 경로 제거
- `ProductCommandService.decreaseLikeCount()` → 호출 경로 제거
- `ProductLikeCountSyncer` port → 제거 (더 이상 in-process 동기화 불필요)
- `ProductLikeCountSyncerImpl` ACL → 제거
- `ProductLikeEventListener.handleLikeCountIncrease/Decrease` → 제거

---

## 신규 ErrorType

```java
COUPON_ISSUE_REQUEST_NOT_FOUND(NOT_FOUND, "COUPON_ISSUE_REQUEST_NOT_FOUND", "쿠폰 발급 요청을 찾을 수 없습니다."),
COUPON_SOLD_OUT(CONFLICT, "COUPON_SOLD_OUT", "쿠폰이 모두 소진되었습니다."),
COUPON_ISSUE_DUPLICATED(CONFLICT, "COUPON_ISSUE_DUPLICATED", "이미 발급된 쿠폰입니다."),
```

---

## 구현 순서 (커밋 단위)

### Phase A: Step 1 — ApplicationEvent

1. `feat: AsyncConfig 및 도메인 이벤트 클래스 추가`
   - AsyncConfig, OrderCreatedEvent, OrderPaidEvent, ProductLikedEvent, ProductUnlikedEvent, ProductViewedEvent

2. `feat: 주문 생성 이벤트 분리 — 장바구니 삭제 AFTER_COMMIT`
   - OrderCommandService에서 장바구니 삭제 제거 + 이벤트 발행
   - OrderEventListener 추가

3. `feat: 좋아요 이벤트 분리 — likeCount eventual consistency`
   - ProductLikeCommandFacade에서 동기 likeCount 제거 + 이벤트 발행
   - ProductLikeEventListener 추가

4. `feat: 결제 완료 이벤트 + 유저 행동 로깅`
   - PaymentCommandService에서 OrderPaidEvent 발행
   - 상품 상세 조회에서 ProductViewedEvent 발행
   - UserActionEventListener 추가

5. `test: Step 1 이벤트 분리 테스트`

### Phase B: Step 2 — Kafka Outbox + Streamer

6. `feat: Outbox 인프라 추가 (공유 모듈 + commerce-api 전용)`
   - 공유 모듈(`support/common/outbox/`): OutboxEventPort, OutboxEventDto, OutboxStatus, OutboxRelayScheduler
   - commerce-api 전용 인프라: OutboxEventApiEntity(`outbox_event_api`), JpaRepo, PortImpl

7. `chore: commerce-api에 kafka 모듈 의존성 추가`

8. `feat: Service에서 Outbox 이벤트 저장 연동`
   - OrderCommandService, PaymentCommandService, ProductLikeCommandService, 상품 조회

9. `feat: Kafka Producer 설정 (acks=all, idempotence)`

10. `feat: Outbox Relay Scheduler 구현`

11. `feat: commerce-streamer Metrics Consumer 구현 (배치 처리 + snapshot outbox)`
    - ProductMetricsEntity, EventHandledEntity, MetricsCollectorConsumer, ProductMetricsService
    - streamer 전용 outbox 인프라: OutboxEventStreamerEntity(`outbox_event_streamer`), JpaRepo, PortImpl
    - BATCH_LISTENER 활용, 배치 내 delta 합산 후 bulk upsert
    - 같은 TX에서 snapshot outbox 저장 → OutboxRelayScheduler(streamer)가 Kafka 발행

12. `feat: commerce-streamer ReadModelSync Consumer 구현`
    - ReadModelSyncConsumer, ReadModelSyncService
    - product-metrics-snapshots 구독, version 비교로 최신만 반영
    - ProductReadModel update + 캐시 evict + event_handled 멱등

13. `feat: commerce-streamer UserActionLog Consumer 구현 (배치 처리)`
    - EventLogEntity, UserActionLogConsumer, UserActionLogService

14. `feat: DLQ 설정 및 event_handled 멱등 처리`

15. `refactor: in-process 경로 제거 (SoT 단일화 + 로그 이중화 해소)`
    - ProductLikeEventListener의 handleLikeCountIncrease/Decrease 제거
    - ProductCommandService.increaseLikeCount/decreaseLikeCount 호출 경로 제거
    - ProductLikeCountSyncer port + ACL 제거
    - UserActionEventListener 제거 (Kafka 중앙 로그로 대체)
    - Kafka → product_metrics → snapshot → ReadModelSyncConsumer 경로로 완전 전환

16. `test: Step 2 Kafka 파이프라인 테스트`

### Phase C: Step 3 — 선착순 쿠폰

17. `refactor: 기존 동기 쿠폰 발급 API 폐기 (D14)`
    - IssuedCouponCommandController 제거
    - IssuedCouponCommandFacade.issueCoupon() 제거

18. `feat: CouponTemplate maxQuantity 필드 추가`

19. `feat: CouponIssueRequest 도메인 모델 및 API`
    - 도메인 모델, Entity, Repository, Controller, Facade, Service
    - POST (202 Accepted, requestId 포함) + GET (polling)

20. `feat: commerce-streamer 쿠폰 발급 Consumer`
    - CouponIssueConsumer, CouponIssueProcessorService

21. `test: 선착순 쿠폰 동시성 테스트`
    - maxQuantity=100, 200건 동시 요청 → 정확히 100건만 발급 검증

### Phase D: Batch 동기화

22. `feat: commerce-batch ProductMetricsReconciliationJob`
    - product_metrics vs product_read_model drift 보정 (안전망)
    - 하루 1회 실행

---

## 수정 대상 핵심 파일

| 파일 | 변경 내용 |
|------|----------|
| `ordering/order/application/service/OrderCommandService.java` | 장바구니 삭제 제거, 이벤트 발행, Outbox 저장 |
| `engagement/productlike/application/facade/ProductLikeCommandFacade.java` | 동기 likeCount 제거, 이벤트 발행 |
| `engagement/productlike/application/service/ProductLikeCommandService.java` | Outbox 저장 추가 |
| `payment/payment/application/service/PaymentCommandService.java` | OrderPaidEvent 발행, Outbox 저장 |
| `catalog/product/application/facade/ProductQueryFacade.java` | `@Transactional(readOnly=true)` → `@Transactional` 변경, 시그니처 변경 `getProduct(id, userId)`, ProductViewedEvent 발행 (모든 조회, userId nullable), Step 2에서 outbox 저장 |
| `catalog/product/interfaces/web/controller/ProductQueryController.java` | 상세 조회에 optional auth resolution 추가 (userId nullable 전달) — `AuthenticationResolver.resolveOptional()` 사용 |
| `support/common/auth/AuthenticationResolver.java` | `resolveOptional(loginId, password)` 메서드 추가: 헤더가 null/blank면 예외 대신 null 반환. 기존 `resolve()`는 변경 없음. |
| `support/common/event/UserActionEventListener.java` | Step 2 이후 제거 (Kafka 중앙 로그로 대체) |
| `payment/payment/application/port/out/client/order/PaymentOrderReader.java` | `findOrderItems(Long orderId)` 메서드 추가 → `List<PaymentOrderItemInfo>` 반환 |
| `payment/payment/application/port/out/client/order/PaymentOrderItemInfo.java` | 신규 record: `productId`, `quantity` |
| `payment/payment/infrastructure/acl/order/PaymentOrderReaderImpl.java` | `findOrderItems()` 구현 — `OrderQueryFacade` 통해 주문 items 조회 |
| `coupon/issuedcoupon/interfaces/web/controller/IssuedCouponCommandController.java` | 제거 (기존 동기 발급 API 폐기, D14) |
| `apps/commerce-api/build.gradle.kts` | kafka 모듈 의존성 추가 |
| `modules/kafka/src/main/resources/kafka.yml` | acks=all, idempotence 설정 |
| `modules/kafka/src/main/java/.../KafkaConfig.java` | DLQ ErrorHandler 추가 |
| `apps/commerce-streamer/` | DemoConsumer 교체, MetricsCollector + UserActionLog + CouponIssue Consumer 구현 |
| `apps/commerce-streamer/.../readmodel/` | ReadModelSyncConsumer + ReadModelSyncService 신규 (snapshot → ReadModel 반영) |
| `apps/commerce-batch/` | ProductMetricsReconciliationJob 추가 (reconciliation/backfill 안전망) |
| `catalog/product/infrastructure/entity/ProductReadModelEntity.java` | `metrics_version` 컬럼 추가 (version 비교용) |
| `support/common/error/ErrorType.java` | 신규 에러 타입 추가 |
| `catalog/product/application/service/ProductCommandService.java` | Step 2에서 increaseLikeCount/decreaseLikeCount 호출 경로 제거 |
| `engagement/productlike/application/port/out/client/catalog/ProductLikeCountSyncer.java` | Step 2에서 제거 (SoT 단일화) |
| `engagement/productlike/infrastructure/acl/catalog/ProductLikeCountSyncerImpl.java` | Step 2에서 제거 |

---

## 검증 방법

### Step 1
- 단위 테스트: 이벤트 발행 verify (mock ApplicationEventPublisher)
- 단위 테스트: 리스너 동작 verify (mock Service)
- 통합 테스트: 주문 생성 → 장바구니 eventually 삭제 확인
- 통합 테스트: 좋아요 생성 → likeCount eventually 증가 확인
- **VIEW 필수 검증**:
  - 비로그인 상품 상세 조회 → PRODUCT_VIEWED 발행 (userId=null) → view_count += 1
  - 로그인 상품 상세 조회 → PRODUCT_VIEWED 발행 (userId 포함) → view_count += 1
  - 공개 상품 조회가 optional auth 추가 후에도 인증 없이 정상 동작 (기존 공개 API 깨지지 않음 확인)

### Step 2
- 통합 테스트: Outbox 저장 → Relay → Kafka 발행 확인 (TestContainers Kafka)
- 통합 테스트: Consumer 수신 → product_metrics upsert 확인
- 멱등 테스트: 동일 이벤트 2회 발행 → metrics 1회만 증가
- DLQ 테스트: Consumer 예외 → DLT 토픽 이동 확인
- snapshot consumer 구버전 version skip 검증: incoming.version <= current.metrics_version → skip 확인
- ReadModel metrics_version 갱신 검증: snapshot 소비 후 product_read_model.metrics_version이 snapshot의 version으로 갱신되는지 확인
- ReadModelSyncConsumer → ProductReadModel 반영 + 캐시 evict 검증: snapshot 소비 → like_count + metrics_version 반영 + 상세 캐시 evict 확인

### Step 3
- API 테스트: POST → 202 Accepted, GET → PENDING/ISSUED/REJECTED
- 동시성 테스트: maxQuantity=100, 200건 → 정확히 100건 발급
- 중복 테스트: 같은 userId+couponTemplateId → 1건만 발급
- 수량 초과 테스트: 발급 완료 후 추가 요청 → REJECTED (COUPON_SOLD_OUT)
