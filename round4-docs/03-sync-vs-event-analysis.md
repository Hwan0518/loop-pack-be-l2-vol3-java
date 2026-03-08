# 동기 vs 이벤트 기반 부수효과 분석

> 분석 기준: `round4/concurrency_control` 브랜치
> 선행 문서: `transaction-query-analysis.md`
> 원칙: **최종적 일관성이 권장되거나 필요한 부분만 이벤트 기반**, 나머지는 동기 유지

---

## 판단 기준 프레임워크

| 기준 | 동기 유지 | 이벤트 분리 |
|------|:---:|:---:|
| 부수효과 실패 시 핵심 작업도 롤백해야 하는가? | O | X |
| 즉각적 정합성이 필요한가? | O | X |
| 대량 데이터 처리로 트랜잭션이 길어질 수 있는가? | X | O |
| soft delete 등으로 지연 정리되어도 안전한가? | X | O |
| 핵심 비즈니스인가, 파생 부수효과인가? | 핵심 | 부수효과 |

---

## 1. 좋아요 생성/취소 → likeCount 동기화

| 구분 | 내용 |
|------|------|
| **설계 문서** | 이벤트 기반 (`ProductLikeCreatedEvent` → `LikeCountSyncEventListener`) |
| **현재 코드** | 동기 (`ProductLikeCountSyncer` Port → `ProductLikeCountCommandFacade`) |
| **분석 보고서** | 발견 3 — Lost Update 가능 (락 없음) |

**근거 분석:**

- `likeCount`는 **파생 데이터**다. `product_likes` 테이블에서 `COUNT(*)`로 언제든 재계산 가능
- 하지만 좋아요를 누르자마자 화면의 숫자가 올라가는 게 자연스러운 UX → **즉각 반영이 바람직**
- 동기 방식의 장점: 좋아요 생성과 카운트 증가의 **원자성 보장** (카운트만 누락되는 비정합 방지)
- 동기 방식의 문제: **Lost Update** — 하지만 이건 이벤트로 바꿔도 동일하게 발생. 근본 해결은 UPDATE 쿼리 직접 실행
- 이벤트 방식의 리스크: 좋아요 생성 성공 → 카운트 증가 실패 → 비정합 발생 + 보정 배치 필요

**최종 목표: 동기 유지 — UPDATE 쿼리 직접 실행으로 Lost Update 해결**

```sql
UPDATE product SET like_count = like_count + 1 WHERE id = ? AND deleted_at IS NULL
```

구현 복잡도가 낮고, Cross-BC 트랜잭션이긴 하지만 좋아요와 카운트의 원자성이 비즈니스적으로 중요하다면 동기가 합리적이다.

---

## 2. 주문 생성 → 장바구니 정리

| 구분 | 내용 |
|------|------|
| **설계 문서** | 이벤트 기반 (`OrderCreatedEvent` → `CartCleanupEventListener`) |
| **현재 코드** | 동기 (`OrderPlacementCommandService.deleteCartItems()`) |
| **분석 보고서** | 발견 1의 일부 — 트랜잭션이 불필요하게 큼 |

**근거 분석:**

- 주문의 핵심은 **재고 차감 + 주문 저장 + 멱등키 저장**. 장바구니 정리는 **부수효과**
- 장바구니 정리 실패가 주문을 롤백시켜야 하는가? → **아니오**. 주문은 완료됐고, 장바구니에 항목이 남아있어도 멱등키로 중복 주문 방지됨
- 장바구니 정리 실패 시 사용자 영향: 이미 주문한 항목이 장바구니에 남아있음 → **불편하지만 치명적이지 않음**
- 장바구니 정리가 다른 BC(Cart)의 쓰기 작업 → **동기라면 주문 트랜잭션이 Cart BC까지 확장**됨
- 트랜잭션 범위 축소 시 재고 락 유지 시간도 줄어듦

**최종 목표: 이벤트 기반 — `OrderCreatedEvent` → `@TransactionalEventListener(AFTER_COMMIT)`**

설계 문서의 원래 방향이 맞다. 주문 트랜잭션에서 가장 쉽게 분리할 수 있는 부수효과이다.

---

## 3. 상품 삭제 → 좋아요 정리

| 구분 | 내용 |
|------|------|
| **설계 문서** | 이벤트 기반 (`ProductDeletedEvent` → `ProductLikeCleanupEventListener`) |
| **현재 코드** | 동기 (`ProductCommandService.deleteAllProductLikes()`) |
| **분석 보고서** | 발견 4 — 심각도 낮음 |

**근거 분석:**

- 상품은 **Soft Delete** → 좋아요가 즉시 정리 안 되어도 데이터 무결성 문제 없음
- 좋아요 목록 조회 시 **삭제된 리소스는 필터링하여 노출하지 않는다** (요구사항 `01-requirements.md`에 명시)
- 좋아요 정리 실패가 상품 삭제를 롤백시켜야 하는가? → **아니오**. 관리자가 의도한 삭제가 좋아요 정리 오류 때문에 실패하면 오히려 문제
- 인기 상품이면 좋아요 수만 건 → **대량 DELETE로 트랜잭션이 길어질 수 있음**

**최종 목표: 이벤트 기반 — `ProductDeletedEvent` → `@TransactionalEventListener(AFTER_COMMIT)`**

Soft Delete + 조회 필터링이 안전장치로 이미 동작하므로, 지연 정리에 적합한 전형적인 케이스이다.

---

## 4. 상품 삭제 → 장바구니 정리

| 구분 | 내용 |
|------|------|
| **설계 문서** | 이벤트 기반 (`ProductDeletedEvent` → `CartCleanupEventListener`) |
| **현재 코드** | 동기 (`ProductCommandService.deleteAllCartItems()`) |

**근거 분석:**

- 상품 Soft Delete 후 장바구니에 해당 상품이 남아있으면? → 사용자가 주문 시도 시 **상품 존재 검증에서 실패** (404)
- 하지만 이건 "주문 시 자연스럽게 발견되는" 문제이지 데이터 손상은 아님
- 장바구니 상태 확인 API(`GET /cart/status`)에서도 삭제된 상품은 감지 가능
- 정리 실패가 상품 삭제를 롤백시켜야 하는가? → **아니오**

**최종 목표: 이벤트 기반 — `ProductDeletedEvent` → 별도 리스너에서 처리**

3번과 동일 이벤트(`ProductDeletedEvent`)로 좋아요와 장바구니 정리를 각각의 리스너에서 처리한다.

---

## 5. 브랜드 삭제 → 좋아요 정리

| 구분 | 내용 |
|------|------|
| **설계 문서** | 이벤트 기반 (`BrandDeletedEvent` → `BrandLikeCleanupEventListener`) |
| **현재 코드** | 동기 (`BrandCommandService.deleteAllBrandLikes()`) |

**근거 분석:**

- 3번과 완전히 동일한 논리. 브랜드도 Soft Delete, 좋아요 조회 시 필터링
- 브랜드 삭제 전 "활성 상품 0개" 검증이 있으므로, 브랜드 삭제 자체가 빈번하지 않음
- 일관성 유지를 위해 이벤트 패턴으로 통일하는 것이 좋음

**최종 목표: 이벤트 기반 — `BrandDeletedEvent` → `@TransactionalEventListener(AFTER_COMMIT)`**

---

## 6. OrderCommandFacade — 읽기 작업의 트랜잭션 포함

| 구분 | 내용 |
|------|------|
| **현재 코드** | 인증, 멱등성 검사, 장바구니 조회, 상품 조회가 모두 쓰기 트랜잭션 내부 |
| **분석 보고서** | 발견 1 — 심각도 높음 |

**근거 분석:**

- 읽기 4건이 쓰기 트랜잭션에 포함 → DB 커넥션을 쓰기 모드로 불필요하게 점유
- 하지만 `decreaseStock()`의 비관적 락은 읽기 이후에 획득되므로 락 유지 시간 문제는 회피됨
- 트랜잭션 분리 시 TOCTOU 우려 → `decreaseStock()`이 비관적 락으로 보호하므로 재고는 안전
- 구현 복잡도: Facade에서 `@Transactional` 제거 → 쓰기 작업만 묶는 별도 오케스트레이션 필요

**최종 목표: 현재 유지 (낮은 우선순위 개선)**

이론적으로 분리가 맞지만, 실질적 영향이 크지 않고 구현 복잡도가 올라간다. 2번(장바구니 정리)이 이벤트로 빠지면 트랜잭션 범위가 자연스럽게 줄어든다.

---

## 전체 요약

| # | 부수효과 | 현재 코드 | 설계 문서 | 최종 목표 | 근거 핵심 |
|---|---|:---:|:---:|:---:|---|
| 1 | 좋아요 → likeCount | 동기 | 이벤트 | **동기** | 원자성 보장이 유리. UPDATE 쿼리로 Lost Update 해결 |
| 2 | 주문 → 장바구니 정리 | 동기 | 이벤트 | **이벤트** | 부수효과. 실패해도 주문 유효. 트랜잭션 범위 축소 |
| 3 | 상품 삭제 → 좋아요 정리 | 동기 | 이벤트 | **이벤트** | Soft Delete + 조회 필터링으로 안전. 대량 가능 |
| 4 | 상품 삭제 → 장바구니 정리 | 동기 | 이벤트 | **이벤트** | 3번과 동일 이벤트. Soft Delete로 안전 |
| 5 | 브랜드 삭제 → 좋아요 정리 | 동기 | 이벤트 | **이벤트** | 3번과 동일 논리 |
| 6 | 주문 Facade 읽기 범위 | 쓰기TX 내 | — | **현재 유지** | 2번 분리로 자연 개선. 추가 분리는 복잡도 대비 이점 낮음 |

---

## 필요 작업 분류

### 코드 변경 (최종 목표 ≠ 현재 코드)

| 대상 | 변경 내용 |
|------|----------|
| `OrderPlacementCommandService` | 장바구니 정리를 `OrderCreatedEvent` 발행으로 변경 |
| `ProductCommandFacade/Service` | 좋아요/장바구니 정리를 `ProductDeletedEvent` 발행으로 변경 |
| `BrandCommandFacade/Service` | 좋아요 정리를 `BrandDeletedEvent` 발행으로 변경 |
| `ProductLikeCountCommandFacade` | `findActiveById()` + `save()` → UPDATE 쿼리 직접 실행으로 변경 |
| EventListener 신규 생성 | `CartCleanupEventListener`, `ProductLikeCleanupEventListener`, `BrandLikeCleanupEventListener` |

### 설계 문서 수정 (최종 목표 ≠ 설계 문서)

| 문서 | 수정 내용 |
|------|----------|
| `02-sequence-diagrams.md` (7-1, 7-2) | 좋아요 등록/취소에 likeCount 동기 호출 단계 추가 |
| `03-class-diagram.md` (섹션 5) | `ProductLikeCountSyncer` Port 의존 추가, 이벤트 기반 likeCount 동기화 제거 |
| `03-class-diagram.md` (섹션 3) | `ProductLikeCreatedEvent`/`ProductLikeCancelledEvent` 제거 |
| `03-class-diagram.md` (섹션 9.1) | likeCount 이벤트 행 제거, 나머지 이벤트는 유지 (최종 목표와 일치) |
| `04-erd-claude.md` (likes 테이블) | `likes` → `product_likes` + `brand_likes` 분리 반영 |

### 이미 일치 (수정 불필요)

| 문서 | 설계 = 최종 목표 |
|------|---|
| `02-sequence-diagrams.md` (9-1) | 장바구니 정리가 이벤트 기반 — 최종 목표와 일치 |
| `03-class-diagram.md` (섹션 9.1) | `OrderCreatedEvent`, `ProductDeletedEvent`, `BrandDeletedEvent` — 최종 목표와 일치 |
| `04-erd-claude.md` (섹션 6.4) | 상품/브랜드 삭제 트랜잭션이 이벤트 기반 — 최종 목표와 일치 |
| `01-requirements.md` | 기능 요구사항 수준에서 변경 없음 |

---

## 결론

설계 문서는 원래 이벤트 기반으로 잘 설계되어 있었으나, 구현 시 전부 동기로 변경되었다.
최종 목표는 **likeCount 동기화만 동기로 유지**하고 나머지 정리 작업은 **원래 설계대로 이벤트로 복원**하는 것이므로, 설계 문서의 수정 범위는 likeCount 관련 항목으로 한정된다.
