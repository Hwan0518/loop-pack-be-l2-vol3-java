# AuthenticationResolver 리팩토링 설계 문서

## 문제

5개 BC(cart, engagement/brandlike, engagement/productlike, ordering, coupon)에 동일한 `UserAuthenticator` Port 인터페이스와 ACL 구현체가 중복 존재.
총 10개 파일(5 Port + 5 ACL)이 완전히 동일한 인증 로직을 수행.

```
{bc}/{domain}/application/port/out/client/user/UserAuthenticator.java     × 5
{bc}/{domain}/infrastructure/acl/user/UserAuthenticatorImpl.java          × 5
```

### 기존 흐름

```
Controller → Facade(loginId, password, ...) → Service.authenticate(loginId, password)
  → UserAuthenticator.authenticate() → (ACL) → UserQueryFacade
```

- 인증이 Service/Facade 레이어 깊숙이 위치
- 모든 BC가 User BC에 대한 Cross-BC Port/ACL을 유지해야 함

## 설계 결정

### 방향 B: AuthenticationResolver 공용 컴포넌트

인증 책임을 Controller 레이어로 이동하고, 공용 `AuthenticationResolver` 컴포넌트 도입.

### 변경 후 흐름

```
Controller → AuthenticationResolver.resolve(loginId, password) → Long userId
Controller → Facade(userId, ...) → Service(userId, ...)
```

### AuthenticationResolver 위치

```
com.loopers.support.common.auth.AuthenticationResolver   (@Component)
com.loopers.support.common.auth.HeaderValidator           (static utility)
```

- `support/common/auth/`에 위치하여 모든 BC에서 접근 가능
- BC 경계를 넘지 않고 공용 인프라로 분류

## 근거

| 기준 | 기존 (Port/ACL) | 변경 (AuthenticationResolver) |
|------|----------------|------------------------------|
| 파일 수 | 10개 (5 Port + 5 ACL) | 1개 |
| 중복 코드 | 동일 코드 5벌 | 없음 |
| 인증 위치 | Service/Facade 내부 | Controller 진입점 |
| Facade 시그니처 | (String loginId, String password, ...) | (Long userId, ...) |
| 새 BC 추가 시 | Port + ACL 2개 파일 추가 | 변경 없음 |

### 왜 Controller 레이어인가

- 인증은 비즈니스 로직이 아닌 인프라 관심사
- HTTP 헤더 검증 → 사용자 인증 → userId 추출은 요청 진입점의 책임
- Facade/Service는 "이미 인증된 userId"를 받아 비즈니스 로직에 집중

### User BC 예외

- `UserQueryController`, `UserCommandController`는 `HeaderValidator`만 직접 사용
- 자기 BC 내부에서 인증 처리하므로 `AuthenticationResolver` 불필요 (import 경로만 변경)

## 변경 파일 요약

| 유형 | 파일 수 | 설명 |
|------|--------|------|
| 신규 생성 | 2 | `AuthenticationResolver`, `HeaderValidator` (공용 이동) |
| 프로덕션 수정 | 24 | Controller 12 + Facade 10 + Service 5 - 중복 = 24 |
| 프로덕션 삭제 | 11 | UserAuthenticator Port 5 + ACL 5 + 기존 HeaderValidator 1 |
| 테스트 수정 | 27 | ServiceTest 6 + FacadeTest 10 + ControllerTest 7 + TestPortConfig 4 |
| **합계** | **66** | 374 insertions, 846 deletions |
