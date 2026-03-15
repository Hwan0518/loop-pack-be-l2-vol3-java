# 08. 전체 도메인 인덱스 추가 및 캐싱 분석

## 1. 배경

상품(Product) 도메인의 Read Model + 복합 인덱스 + Redis 캐시 적용 이후,
전체 도메인에 대해 인덱스 누락 여부를 점검하고 캐싱 기회를 분석하였다.

---

## 2. ProductReadModelEntity 인덱스 보완

### 2-1. 기존 인덱스의 한계

기존 3개 인덱스는 `(deleted_at, brand_id, {sort_col})` 구조였으나, 두 가지 문제가 있었다:

1. **컬럼 순서**: `deleted_at`(카디널리티 2: NULL/timestamp)이 `brand_id`(카디널리티 수십~수백)보다 앞에 위치 → B-tree fan-out 불균등
2. **커버리지 부족**: brandId 없는 사용자 쿼리와 관리자 쿼리에서 인덱스 활용 불가

**카디널리티 우선 원칙으로 순서 변경**: `(brand_id, deleted_at, {sort_col})`
- 두 컬럼 모두 equality 조건이므로 인덱스 탐색 결과(matching rows)는 순서 무관하게 동일
- 카디널리티가 높은 `brand_id`를 선두에 배치하면 B-tree 첫 레벨의 분기가 더 균등해져 인덱스 페이지 접근 효율 향상

**brandId 없는 사용자 쿼리의 문제:**
```
WHERE deleted_at IS NULL ORDER BY created_at DESC
```
인덱스 `(brand_id, deleted_at, created_at)`에서 선두 컬럼 `brand_id`가 쿼리에 없으므로 인덱스 활용 불가 → 별도 2-column 인덱스 `(deleted_at, sort_col)` 필요.

**관리자 쿼리의 문제:**
```
WHERE brand_id = ? ORDER BY created_at DESC  (또는 필터 없음)
```
3-column 인덱스의 `deleted_at`이 쿼리에 없으므로 정렬 컬럼까지 도달 불가 → 별도 2-column 인덱스 `(brand_id, sort_col)` 필요.

### 2-2. 보완된 인덱스 (기존 3 → 총 12개)

| # | 조합 | 인덱스 컬럼 | 상태 |
|---|------|------------|:----:|
| 1 | 사용자 + 브랜드 + LATEST | `(brand_id, deleted_at, created_at)` | 기존 (순서 변경) |
| 2 | 사용자 + 브랜드 + PRICE_ASC | `(brand_id, deleted_at, price)` | 기존 (순서 변경) |
| 3 | 사용자 + 브랜드 + LIKES_DESC | `(brand_id, deleted_at, like_count)` | 기존 (순서 변경) |
| 4 | 사용자 + 전체 + LATEST | `(deleted_at, created_at)` | **신규** |
| 5 | 사용자 + 전체 + PRICE_ASC | `(deleted_at, price)` | **신규** |
| 6 | 사용자 + 전체 + LIKES_DESC | `(deleted_at, like_count)` | **신규** |
| 7 | 관리자 + 브랜드 + LATEST | `(brand_id, created_at)` | **신규** |
| 8 | 관리자 + 브랜드 + PRICE_ASC | `(brand_id, price)` | **신규** |
| 9 | 관리자 + 브랜드 + LIKES_DESC | `(brand_id, like_count)` | **신규** |
| 10 | 관리자 + 전체 + LATEST | `(created_at)` | **신규** |
| 11 | 관리자 + 전체 + PRICE_ASC | `(price)` | **신규** |
| 12 | 관리자 + 전체 + LIKES_DESC | `(like_count)` | **신규** |

**커버리지:** 사용자/관리자 × 브랜드유무 × 3개 정렬 = **12개 조합 모두 인덱스 커버**.

---

## 3. 타 도메인 인덱스 추가

### 3-1. 추가된 인덱스 요약

| 엔티티 | 테이블 | 인덱스명 | 컬럼 | 대상 쿼리 |
|--------|--------|---------|------|----------|
| BrandEntity | `brands` | `idx_brands_deleted_visible` | `(deleted_at, visible_status)` | 브랜드 목록 조회 (사용자/관리자) |
| OrderEntity | `orders` | `idx_orders_user_created` | `(user_id, created_at)` | 주문 내역 조회 + 기간 필터 |
| OrderItemEntity | `order_items` | `idx_order_items_order` | `(order_id)` | 주문 상품 조회 |
| CartItemEntity | `cart_items` | `idx_cart_user_selected` | `(user_id, selected)` | 선택된 장바구니 항목 조회 |
| CartItemEntity | `cart_items` | `idx_cart_product` | `(product_id)` | 상품 삭제 시 장바구니 정리 (※ 처리 방식 미확정 — 논의 중) |
| ProductLikeEntity | `likes` | `idx_likes_user_type_created` | `(user_id, target_type, created_at)` | 좋아요 목록 페이지네이션 |
| ProductLikeEntity | `likes` | `idx_likes_type_target` | `(target_type, target_id)` | 상품/브랜드 삭제 시 좋아요 정리 (※ 즉시 정리 제거 확정 — 배치 잡 정리 시 활용 가능) |
| IssuedCouponEntity | `issued_coupon` | `idx_issued_coupon_user_created` | `(user_id, created_at)` | 사용자 쿠폰 내역 |
| IssuedCouponEntity | `issued_coupon` | `idx_issued_coupon_template_created` | `(coupon_template_id, created_at)` | 관리자 쿠폰 발급 내역 |
| CouponTemplateEntity | `coupon_template` | `idx_coupon_template_deleted` | `(deleted_at)` | 활성 쿠폰 템플릿 목록 |

### 3-2. 엔티티별 상세 분석

#### BrandEntity (`brands`)

**기존 인덱스:** 없음 (PK만 존재)

**쿼리 패턴:**
- `findAllByDeletedAtIsNull(Pageable)` — 관리자 브랜드 목록
- `findAllByVisibleStatusAndDeletedAtIsNull(VisibleStatus, Pageable)` — 사용자 브랜드 목록

**추가 인덱스:**
```
idx_brands_deleted_visible (deleted_at, visible_status)
```
- `deleted_at`이 선두: `findAllByDeletedAtIsNull` 쿼리에서도 인덱스 prefix 활용 가능
- `visible_status`가 후순위: 사용자 조회 시 추가 필터링

---

#### OrderEntity (`orders`)

**기존 인덱스:** `UNIQUE (user_id, request_id)` — 멱등성 체크용

**쿼리 패턴:**
- `findByUserId(Pageable)` — 사용자 주문 내역 (ORDER BY created_at DESC)
- `findByUserIdAndCreatedAtInRange(userId, start, end, Pageable)` — 기간별 주문 내역

**추가 인덱스:**
```
idx_orders_user_created (user_id, created_at)
```
- `user_id` equality + `created_at` range/sort를 단일 인덱스로 커버
- 두 쿼리 패턴 모두 지원: 전체 조회(user_id만) + 기간 필터(user_id + created_at range)

---

#### OrderItemEntity (`order_items`)

**기존 인덱스:** 없음 (PK만 존재, FK 인덱스 자동 생성 안 됨)

**쿼리 패턴:**
- `findByOrderId(orderId)` — 단일 주문의 상품 목록
- `findByOrderIdIn(orderIds)` — 복수 주문의 상품 목록 (배치)

**추가 인덱스:**
```
idx_order_items_order (order_id)
```
- `order_id`가 `@ManyToOne`이 아닌 plain Long 필드이므로 FK 인덱스가 자동 생성되지 않음
- 주문 상세 조회 시 필수적으로 사용되는 쿼리

---

#### CartItemEntity (`cart_items`)

**기존 인덱스:** `UNIQUE (user_id, product_id)` — 동일 상품 중복 방지

**쿼리 패턴:**
- `findByUserId(userId)` — 장바구니 전체 조회 (UNIQUE prefix로 커버 ✅)
- `findByUserIdAndProductId(userId, productId)` — UNIQUE 인덱스로 커버 ✅
- `findByUserIdAndSelectedTrue(userId)` — 선택된 항목만 조회 (**커버 안 됨**)
- `deleteAllByProductId(productId)` — 상품 삭제 시 장바구니 정리 (**커버 안 됨**)

**추가 인덱스:**
```
idx_cart_user_selected (user_id, selected)
idx_cart_product (product_id)
```

---

#### ProductLikeEntity / BrandLikeEntity (`likes` 공유 테이블)

**기존 인덱스:** `UNIQUE (user_id, target_type, target_id)` — 중복 좋아요 방지

**쿼리 패턴:**
- `findByUserIdAndTargetTypeAndTargetId(...)` — UNIQUE로 커버 ✅
- `existsByUserIdAndTargetTypeAndTargetId(...)` — UNIQUE로 커버 ✅
- `findByUserIdAndTargetType(userId, targetType, Pageable)` — 좋아요 목록 (**ORDER BY created_at 미커버**)
- `deleteAllByTargetTypeAndTargetId(targetType, targetId)` — 삭제 시 정리 (**target_type, target_id 조합 미커버**)

**추가 인덱스:**
```
idx_likes_user_type_created (user_id, target_type, created_at)
idx_likes_type_target (target_type, target_id)
```
- 두 엔티티가 동일 테이블을 공유하므로 `ProductLikeEntity`에서 한 번만 정의
- `idx_likes_user_type_created`: 좋아요 목록 페이지네이션 시 filesort 제거
- `idx_likes_type_target`: 상품/브랜드 삭제 시 관련 좋아요 일괄 삭제 최적화 (※ 즉시 정리 제거 확정 — Soft delete 필터링으로 충분. 인덱스는 향후 배치 잡 정리 시 활용 가능하므로 유지)

---

#### IssuedCouponEntity (`issued_coupon`)

**기존 인덱스:** `UNIQUE (user_id, coupon_template_id)` — 1인 1쿠폰 보장

**쿼리 패턴:**
- `existsByCouponTemplateIdAndUserId(...)` — UNIQUE로 커버 ✅
- `findAllByUserIdOrderByCreatedAtDesc(userId)` — 사용자 쿠폰 내역 (**ORDER BY 미커버**)
- `findAllByCouponTemplateIdOrderByCreatedAtDesc(couponTemplateId)` — 관리자 발급 내역 (**미커버**)

**추가 인덱스:**
```
idx_issued_coupon_user_created (user_id, created_at)
idx_issued_coupon_template_created (coupon_template_id, created_at)
```

---

#### CouponTemplateEntity (`coupon_template`)

**기존 인덱스:** 없음 (PK만 존재)

**쿼리 패턴:**
- `findAllByDeletedAtIsNull(Pageable)` — 활성 쿠폰 템플릿 목록 (관리자)

**추가 인덱스:**
```
idx_coupon_template_deleted (deleted_at)
```

---

#### UserEntity (`users`) — 변경 없음

**기존 인덱스:** `UNIQUE active_login_id` (generated column) — 로그인 조회 최적화

**쿼리 패턴:**
- `findByLoginIdValueAndDeletedAtIsNull(loginId)` — generated column 유니크 인덱스로 커버 ✅
- `existsByLoginIdValueAndDeletedAtIsNull(loginId)` — 위와 동일 ✅

**추가 인덱스 불필요.**

---

## 4. 캐싱 기회 분석

### 4-1. 높은 우선순위 (HIGH)

#### 브랜드 목록 — `GET /api/v1/brands`

| 항목 | 값 |
|------|---|
| 트래픽 | 사용자 대면, 매 페이지 로드마다 호출 |
| 데이터 특성 | 변경 빈도 매우 낮음 (관리자만 수정) |
| 쿼리 비용 | 단순 SELECT + pagination |
| 추천 TTL | **1시간** |
| 캐시 키 | `brand:visible:page:{page}:{size}` |
| 무효화 | 브랜드 생성/수정/삭제/노출상태 변경 시 패턴 삭제 |

#### 브랜드 상세 — `GET /api/v1/brands/{brandId}`

| 항목 | 값 |
|------|---|
| 트래픽 | 사용자 대면, 상품 상세 진입 시 함께 조회 |
| 데이터 특성 | 거의 불변 (이름, 설명만 가끔 수정) |
| 쿼리 비용 | 단건 PK 조회 (저비용이나 빈도가 높음) |
| 추천 TTL | **2시간** |
| 캐시 키 | `brand:detail:{brandId}` |
| 무효화 | 브랜드 수정/삭제 시 개별 키 삭제 |

#### 발급 쿠폰 목록 — `GET /api/v1/users/me/coupons`

| 항목 | 값 |
|------|---|
| 트래픽 | 사용자 대면, 쿠폰함 조회 |
| 데이터 특성 | 변경 빈도 낮음 (발급/사용 시에만 변경) |
| 쿼리 비용 | **N+1 패턴** — 사용자의 발급 쿠폰 조회 후, 각 쿠폰의 템플릿 정보 개별 조회 |
| 추천 TTL | **15분** |
| 캐시 키 | `user:{userId}:issued-coupons` |
| 무효화 | 쿠폰 발급/사용 시 해당 사용자 키 삭제 |

### 4-2. 중간 우선순위 (MEDIUM)

#### 좋아요 여부 확인 — `GET /api/v1/users/me/product-likes/check`, `brand-likes/check`

| 항목 | 값 |
|------|---|
| 트래픽 | 사용자 대면, 상품/브랜드 브라우징마다 호출 |
| 데이터 특성 | boolean 결과, 좋아요 토글 시에만 변경 |
| 쿼리 비용 | EXISTS 쿼리 (저비용이나 매우 빈번) |
| 추천 TTL | **30분** |
| 캐시 키 | `user:{userId}:liked:product:{targetId}` / `brand:{targetId}` |
| 무효화 | 좋아요 등록/취소 시 해당 키 삭제 |

#### 좋아요 목록 — `GET /api/v1/users/me/product-likes`, `brand-likes`

| 항목 | 값 |
|------|---|
| 트래픽 | 사용자 대면, 마이페이지 접근 시 조회 |
| 데이터 특성 | 세션 중 비교적 정적 |
| 쿼리 비용 | 페이지네이션 쿼리 |
| 추천 TTL | **30분** |
| 캐시 키 | `user:{userId}:likes:product:page:{page}:{size}` |
| 무효화 | 좋아요 등록/취소 시 패턴 삭제 |

#### 장바구니 — `GET` (findByUserId)

| 항목 | 값 |
|------|---|
| 트래픽 | 사용자 대면, 쇼핑 세션 중 빈번 조회 |
| 데이터 특성 | 변경 잦음 (수량 변경, 선택/해제, 추가/삭제) |
| 쿼리 비용 | 단순 SELECT (저비용) |
| 추천 TTL | **5분** (짧은 TTL 필수) |
| 캐시 키 | `user:{userId}:cart:items` |
| 무효화 | 장바구니 항목 변경 시 해당 사용자 키 삭제 |

### 4-3. 낮은 우선순위 (LOW) — 캐싱 미권장

| 대상 | 미권장 사유 |
|------|-----------|
| 관리자 쿼리 전반 | 트래픽 미미, 실시간 데이터 필요 |
| 주문 내역 (`GET /api/v1/orders`) | 날짜 파라미터로 캐시 키 폭발, 주문 상태 변경 빈번 |
| 주문 상세 (`GET /api/v1/orders/{id}`) | 주문 상태 변경이 잦아 무효화 비용 > 캐싱 이점 |
| 사용자 인증/정보 (`GET /api/v1/users/me`) | 보안 민감 정보, 비밀번호 변경 등으로 캐싱 부적합 |

---

## 5. 수정 파일 목록

| 파일 | 변경 내용 |
|------|----------|
| `ProductReadModelEntity.java` | 9개 복합 인덱스 추가 (총 12개) |
| `BrandEntity.java` | `idx_brands_deleted_visible` 인덱스 추가 |
| `OrderEntity.java` | `idx_orders_user_created` 인덱스 추가 |
| `OrderItemEntity.java` | `idx_order_items_order` 인덱스 추가 |
| `CartItemEntity.java` | `idx_cart_user_selected`, `idx_cart_product` 인덱스 추가 |
| `ProductLikeEntity.java` | `idx_likes_user_type_created`, `idx_likes_type_target` 인덱스 추가 |
| `IssuedCouponEntity.java` | `idx_issued_coupon_user_created`, `idx_issued_coupon_template_created` 인덱스 추가 |
| `CouponTemplateEntity.java` | `idx_coupon_template_deleted` 인덱스 추가 |
