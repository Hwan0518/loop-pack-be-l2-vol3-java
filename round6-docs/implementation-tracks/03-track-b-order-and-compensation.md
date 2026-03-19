# Track B — Order 변경 + 보상 포트

> **병렬 실행**: Track A (`02-track-a`)와 **동시 진행 가능**
> **선행 조건**: `01-prerequisite-error-types.md` 완료. Step 7은 선행 없음 (즉시 시작 가능)
> **후행 트랙**: Track C (`04-track-c-merge`) — Track A + B 완료 후 시작
> **포함 Step**: 2, 7

---

## Step 2. OrderStatus enum + Order 모델 변경

**선행**: Step 1 (Track A에서 ErrorType 추가 완료 후)

**신규 파일**:
- `ordering/order/domain/model/enums/OrderStatus.java` — enum + 상태 전이 검증 (`canTransitionTo()`)

**수정 파일**:
- `ordering/order/domain/model/Order.java` — `status` 필드 추가, `changeStatus()` 메서드, `create()`에서 `PENDING_PAYMENT` 기본값
- `ordering/order/infrastructure/entity/OrderEntity.java` — `status` 컬럼 추가 (default `PENDING_PAYMENT`)
- `ordering/order/infrastructure/mapper/OrderEntityMapper.java` — status 매핑

**테스트 (Red 먼저)**:
- `OrderStatusTest` — 허용된 전이 성공, 금지된 전이 예외 (PAID→any, EXPIRED→any 등)
- `OrderTest` — `changeStatus()` 동작, 기본값 PENDING_PAYMENT 확인
- `OrderEntityMapper` 테스트 — status 매핑 확인

**근거**: Part 1 §2.4, §2.5

---

## Step 7. Ordering BC → Catalog/Coupon BC 보상 포트

**선행**: 없음 (Step 2와 병렬 가능. 기존 Catalog/Coupon BC만 수정)

**수정 파일**:
- `ordering/order/application/port/out/client/catalog/OrderStockManager.java` — `restoreStock(productId, quantity)` 메서드 추가
- Catalog BC: `ProductCommandFacade.increaseStock()` 추가 (Provider)
- `ProductCommandService.increaseStock()` + `Product.increaseStock()` 도메인 메서드

**신규 파일**:
- `ordering/order/application/port/out/client/coupon/OrderCouponRestorer.java` — interface: `restoreCoupon(issuedCouponId)`
- `ordering/order/infrastructure/acl/coupon/OrderCouponRestorerImpl.java` — `@Component`
- Coupon BC: `IssuedCouponCommandFacade.restoreCoupon()` 추가 (Provider)

**Coupon BC Provider 측 도메인/서비스 변경** (필수):
- `IssuedCoupon.java` — `restore()` 도메인 메서드 추가 (status: USED → ISSUED 복원)
- `IssuedCouponCommandService.java` — `restoreCoupon(issuedCouponId)` 서비스 메서드 추가
- `IssuedCouponCommandFacade.java` — `restoreCoupon(issuedCouponId)` Facade 메서드 추가
- **근거**: 현재 IssuedCoupon 모델에는 `use()`만 존재. 쿠폰 복원을 위한 역방향 상태 전이 메서드가 필요.

**테스트**: 각 Port의 단위 테스트 + Provider 측 서비스 테스트

**근거**: Part 1 §6.3, P1-7

---

## Track B 완료 산출물

| 산출물 | Track C에서 사용 |
|--------|----------------|
| OrderStatus enum + Order.changeStatus() | Step 5 (ACL), Step 12 (상태 변경), Step 19 (만료) |
| OrderEntity status 컬럼 | Step 5, 12 |
| OrderStockManager.restoreStock() | Step 19 (주문 만료 보상) |
| OrderCouponRestorer.restoreCoupon() | Step 19 (주문 만료 보상) |
| IssuedCoupon.restore() + Service/Facade | Step 19 |
| Product.increaseStock() + Service/Facade | Step 19 |
