# Service-Port 통합 리팩토링 설계 문서

## 문제

Port 호출만을 위한 별도 "wrapper Service"가 4개 존재. 비즈니스 로직 없이 Port에 단순 위임만 수행하여 과분리 상태.

## 근거

Application Service의 책임은 "흐름을 조율해 유스케이스를 완결하는 것".
Repository는 내부 저장소, Port는 외부 저장소로 보면, Port 호출만을 위한 별도 Service 생성은 과분리.

## 통합 대상

| Wrapper Service | Port 의존 | 통합 대상 |
|----------------|----------|----------|
| `ProductLikeCountSyncCommandService` | `ProductLikeCountSyncer` | `ProductLikeCommandService` |
| `ProductCleanupCommandService` | `ProductLikeCleanupManager`, `CartItemCleanupManager` | `ProductCommandService` |
| `BrandCleanupCommandService` | `BrandLikeCleanupManager` | `BrandCommandService` |
| `OrderCleanupCommandService` | `OrderCartItemCleaner` | `OrderPlacementCommandService` |

## 유지 대상 (변경 없음)

- `OrderCheckoutCommandService` (4 ports) — 데드락 방지 정렬, 빈 장바구니 검증 등 실질적 비즈니스 로직 보유
- `OrderIdempotencyQueryService` (1 repo) — CQRS Query 분리 원칙 준수

## 순환 의존 해결

통합 후 Cross-BC ACL 경유 순환 의존이 발생:

```
ProductCommandService → ProductLikeCleanupManager → (ACL) → ProductLikeCommandFacade
  → ProductLikeCommandService → ProductLikeCountSyncer → (ACL) → ProductLikeCountCommandFacade
  → ProductCommandService  ← CIRCULAR
```

**해결**: `ProductCommandService`의 Cross-BC Port (`ProductLikeCleanupManager`, `CartItemCleanupManager`)에 `@Lazy`를 적용하여 지연 초기화로 순환 의존을 해소.
`@RequiredArgsConstructor` 대신 수동 생성자를 사용하여 `@Lazy` 파라미터 어노테이션 적용.

## 아키텍처 규칙 변경

- CLAUDE.md, SKILL.md에 추가: "Service는 Repository와 Port를 자유롭게 조합 가능. Port만 감싸는 wrapper Service 금지"

## 변경 파일 요약

| 유형 | 파일 수 |
|------|--------|
| 규칙 수정 | 2 (CLAUDE.md, SKILL.md) |
| 프로덕션 수정 | 8 (Service 4 + Facade 4) |
| 테스트 수정 | 8 (ServiceTest 4 + FacadeTest 4) |
| 프로덕션 삭제 | 4 (wrapper Service) |
| 테스트 삭제 | 4 (대응 테스트) |
