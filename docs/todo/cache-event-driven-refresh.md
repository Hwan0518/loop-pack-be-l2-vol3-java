# TODO: 캐시 갱신 이벤트 기반 전환

## 개요

현재 캐시 write-through는 `@Transactional` 내부에서 동기적으로 실행된다.
TX 롤백 시 캐시에 잘못된 데이터가 남을 수 있으며, TTL 안전망(2~3분)에 의존한다.

이벤트 기반으로 전환하면 TX 커밋 확정 후에만 캐시를 갱신하여 정합성을 보장할 수 있다.

## 현재 상태 (Round 5)

```java
// ProductCommandService — TX 내부에서 캐시 직접 갱신
@Transactional
public void increaseLikeCount(Long productId) {
    readModelRepository.increaseLikeCount(productId);
    // 캐시 write-through (TX 내부 — 롤백 시 캐시 불일치 가능)
    productCacheManager.refreshProductDetail(productId, () -> productQueryPort.findProductCacheDtoById(productId));
}
```

## 목표 상태

```java
// ProductCommandService — 이벤트 발행만
@Transactional
public void increaseLikeCount(Long productId) {
    readModelRepository.increaseLikeCount(productId);
    // 이벤트 발행 (TX 내부에서는 캐시 미접촉)
    eventPublisher.publishEvent(new ProductCacheRefreshEvent(productId, RefreshType.LIKE_COUNT));
}

// 이벤트 리스너 — TX 커밋 후 실행
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onProductCacheRefresh(ProductCacheRefreshEvent event) {
    productCacheManager.refreshProductDetail(event.productId());
    productCacheManager.refreshIdLists(event.productId(), event.refreshType());
}
```

## 변환 대상 메서드

| 메서드 | 현재 | 목표 |
|--------|------|------|
| `ProductCommandService.increaseLikeCount()` | TX 내 캐시 갱신 | 이벤트 발행 |
| `ProductCommandService.decreaseLikeCount()` | TX 내 캐시 갱신 | 이벤트 발행 |
| `ProductCommandService.decreaseStock()` | TX 내 캐시 갱신 | 이벤트 발행 |
| `ProductCommandService.createProduct()` | TX 내 캐시 갱신 | 이벤트 발행 |
| `ProductCommandService.updateProduct()` | TX 내 캐시 갱신 | 이벤트 발행 |
| `ProductCommandService.deleteProduct()` | TX 내 캐시 갱신 | 이벤트 발행 |

## 신규 생성 파일

| 파일 | 위치 | 역할 |
|------|------|------|
| `ProductCacheRefreshEvent` | `catalog/product/domain/event/` | 캐시 갱신 이벤트 (productId, refreshType) |
| `ProductCacheRefreshListener` | `catalog/product/interfaces/event/` | `@TransactionalEventListener` 리스너 |

## 참고: CLAUDE.md 이벤트 규칙

- Event 클래스 Javadoc에 `@subscriber` 목록 명시
- Publisher 쪽 주석에 `→ [{Listener}] {효과}` 인라인 주석 기록

## 참고: ApplicationReadyEvent 기반 캐시 웜업

서버 시작 시 hot 페이지를 선제적으로 캐시 적재하는 기능도 함께 도입한다.

```java
@EventListener(ApplicationReadyEvent.class)
public void warmUpCache() {
    // 모든 필터 조합 × 3정렬 × pages 0~2 캐시 선적재
    // 상품 상세 캐시도 hot 상품 기준으로 선적재
}
```

## 우선순위

- 이벤트 기반 캐시 갱신: **중간** (현재 TTL 안전망으로 동작 중, TX 롤백은 극히 드묾)
- ApplicationReadyEvent 웜업: **낮음** (cache-aside로 초기 적재 가능)

## 관련 문서

- `round5-docs/08-cache-eviction-analysis.md` — 캐시 전략 분석 및 최종 설계
- `docs/design/05-concurrency-strategy.md` — 동시성 전략
- `round4-docs/03-sync-vs-event-analysis.md` — 이벤트 vs 동기 분석
