# CLAUDE.md

## 1. 기술스택 및 버전

| 구분 | 기술 | 버전 |
|------|------|------|
| Language | Java | 21 |
| Language | Kotlin | 2.0.20 |
| Framework | Spring Boot | 3.4.4 |
| Framework | Spring Cloud | 2024.0.1 |
| Build Tool | Gradle (Kotlin DSL) | - |
| API Docs | SpringDoc OpenAPI | 2.7.0 |
| ORM | QueryDSL | Jakarta |
| Test | Spring MockK | 4.0.2 |
| Test | Mockito | 5.14.0 |
| Test | Instancio JUnit | 5.0.2 |
| Test | TestContainers | (MySQL, Redis, Kafka) |
| Lint | ktLint | 1.0.1 |

### 1.1 빌드 및 테스트 명령어

| 명령어 | 설명 |
|--------|------|
| `./gradlew :apps:commerce-api:test` | commerce-api 모듈 테스트 |
| `./gradlew :apps:commerce-api:build` | commerce-api 모듈 빌드 |
| `./gradlew test` | 전체 테스트 |
| `./gradlew clean build` | 클린 빌드 |
| `./gradlew :apps:commerce-api:jacocoTestReport` | commerce-api 커버리지 리포트 생성 |

## 2. 모듈 구조

```
apps/
├── commerce-api      # REST API 서버 (Spring Web + JPA + Redis)
├── commerce-streamer # Kafka 스트림 처리
└── commerce-batch    # Spring Batch 배치 작업

modules/
├── jpa               # JPA + QueryDSL + MySQL (TestContainers)
├── redis             # Spring Data Redis (TestContainers)
└── kafka             # Spring Kafka (TestContainers)

supports/
├── jackson           # Jackson 직렬화 설정
├── logging           # 로깅 설정 (Prometheus + Brave + Slack Appender)
└── monitoring        # 모니터링 설정 (Prometheus)
```

## 3. 패키지 구조 (commerce-api)

```
com.loopers
├── CommerceApiApplication.java    # Spring Boot 진입점
└── {bc}/                          # Bounded Context (예: catalog, engagement, ordering, cart, user)
    └── {domain}/                  # 도메인별 패키지 (예: brand, product, like, order, cart, user)
        ├── application/               # 애플리케이션 서비스 레이어
        │   └── service/               # 애플리케이션 서비스
        │   └── facade/                # 퍼사드 서비스
        │   └── port/out/              # 아웃바운드 포트
        │       └── client/            # Cross-BC 접근 인터페이스
        │           └── {other-domain}/
        │               └── {OtherDomain}Port
        │       └── query/             # 유스케이스 전용 복잡 조회
        │           └── {Domain}QueryPort
        │           └── criteria/      # 조회 조건 객체
        │       └── util/              # 유틸리티 포트 (PasswordEncoder 등)
        │   └── dto/                   # DTO (InDto/OutDto)
        │       └── in/                # 입력 DTO (Request → InDto)
        │       └── out/               # 출력 DTO (Domain → OutDto → Response)
        ├── domain/
        │   └── model/                 # 도메인 모델
        │       └── enums/             # 도메인 내 공통 Enum
        │       └── vo/                # Value Object (예: Password)
        │   └── repository/            # 리포지토리 인터페이스 (CQRS)
        │       └── {Domain}CommandRepository  # 명령 (save, delete)
        │       └── {Domain}QueryRepository    # 조회 (find, exists)
        │       └── vo/                # 페이지네이션 VO (PageCriteria, PageResult)
        │   └── event/                 # 도메인 이벤트
        │   └── service/               # 도메인 서비스
        ├── infrastructure/            # 인프라 레이어 (Repository 구현 등)
        │   └── jpa/                   # JPA 레포지토리
        │   └── repository/            # 리포지토리 구현체 (CQRS)
        │       └── {Domain}CommandRepositoryImpl  # 명령 구현체
        │       └── {Domain}QueryRepositoryImpl    # 조회 구현체
        │   └── acl/                   # Cross-BC 접근 구현체
        │       └── {other-domain}/
        │           └── {OtherDomain}PortImpl
        │   └── query/                 # QueryPort 구현체
        │       └── {Domain}QueryPortImpl
        │   └── querydsl/             # QueryDSL 쿼리 구현체
        │       └── {Domain}QuerydslRepository
        │   └── entity/                # JPA 엔티티
        │   └── mapper/                # Entity ↔ Domain 변환 매퍼
        ├── interfaces/                # 프레젠테이션 레이어 (Controller)
        │   └── web/                   # REST API
        │       └── controller/        # REST 컨트롤러
        │       └── request/           # 요청 객체
        │       └── response/          # 응답 객체
        │   └── event/                 # 이벤트 리스너
        └── support/                   # 도메인 내 공통 모듈
            └── common/                # 공통 유틸리티 (예: Mapper, Validator, PasswordEncoder 등)
                └── error/             # 에러 핸들링 (CoreException, ErrorType)
            └── config/                # 도메인별 설정 관련 (예: Kafka Producer/Consumer 설정 등)
```

## 4. 개발규칙

### 4.1 진행 Workflow - 증강 코딩

| 원칙 | 설명 |
|------|------|
| **대원칙** | 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행 |
| **중간 결과 보고** | AI가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입 |
| **설계 주도권 유지** | AI가 임의판단을 하지 않고, 방향성에 대한 제안 등을 진행할 수 있으나 개발자의 승인을 받은 후 수행 |

### 4.2 주석 컨벤션 (Comment-First Design)

- **모든 코드 작성 시** `.claude/skills/comment-style/SKILL.md` 규칙을 반드시 준수
- **주석 선행 작성**: 주석으로 논리 흐름을 먼저 스케치하고, 그에 맞춰 코드를 채운다 (Comment-First)
- Javadoc(클래스 문서화) + `// N.` (메서드 번호 매칭) + `//` 인라인 주석(논리 단계) 필수
- 비즈니스 설명은 한국어, 구조적 마커(`// service`, `// repository` 등)는 영어

### 4.3 개발 Workflow - TDD (Red > Green > Refactor)

- 모든 테스트는 **3A 원칙**으로 작성: Arrange - Act - Assert

| Phase | 설명 |
|-------|------|
| **1. Red Phase** | 실패하는 테스트 먼저 작성. 요구사항을 만족하는 기능 테스트 케이스 작성 |
| **2. Green Phase** | 테스트를 통과하는 코드 작성. Red Phase의 테스트가 모두 통과할 수 있는 코드 작성. 오버엔지니어링 금지 |
| **3. Refactor Phase** | 불필요한 코드 제거 및 품질 개선. 불필요한 private 함수 지양, 객체지향적 코드 작성. unused import 제거. 성능 최적화. 모든 테스트 케이스가 통과해야 함 |

### 4.4 테스트 컨벤션

#### 테스트 커버리지 목표
- **100%에 가깝게** 테스트 커버리지를 채울 것
- 모든 public 메서드, 분기(branch), 예외 케이스를 테스트로 검증

#### @DisplayName 작성 규칙 (필수)
- **형식**: `[메서드명()] 조건 -> 결과. 상세 설명`
- **아주 자세하게 작성**: 테스트만 보고도 요구사항을 파악할 수 있어야 함
- 예시:
  - `[POST /api/v1/users] 유효한 회원가입 요청 -> 201 Created. 응답: id, loginId, name, birthDate, email 포함`
  - `[UserCommandRepository.save()] 유효한 User 저장 -> ID가 할당된 User 반환`
  - `[Password.create()] 8자 미만 비밀번호 -> INVALID_PASSWORD_FORMAT 예외`

#### 예외 검증 패턴
```java
// Act
CoreException exception = assertThrows(CoreException.class,
    () -> targetMethod(args));

// Assert
assertAll(
    () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.XXX),
    () -> assertThat(exception.getMessage()).isEqualTo(ErrorType.XXX.getMessage())
);
```

#### 통합 테스트 설정
```java
@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
class SomeIntegrationTest {
    // ...
}
```

#### E2E 테스트 패턴
- 위치: `src/test/java/com/loopers/{domain}/interfaces/{Domain}ControllerE2ETest.java` (controller 하위가 아님)
- 어노테이션: `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})`
- 테스트 격리: `@AfterEach`에서 `DatabaseCleanUp.truncateAllTables()` 호출 (`@Transactional` 미사용)
- 테스트 데이터: API 호출 헬퍼 메서드로 직접 생성 (예: `signUpUser()`)
- 테스트 구조: `@Nested` 클래스로 엔드포인트별 그룹화

#### 단위 테스트 패턴
- 모든 테스트 더블(Fake, Stub, Mock, Spy) 사용 가능 — 상황에 맞게 적절한 것을 선택
- Mock 사용 시: `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@BeforeEach`에서 수동 생성자 주입
- BDDMockito: `given().willReturn()`, `willDoNothing()`, `willThrow()`
- 검증: `verify()`, `never()`
- 헤더 검증 파라미터화: `@ParameterizedTest` + `@NullAndEmptySource` + `@ValueSource(strings = {"  ", "\t"})`

#### ErrorType 추가 시 체크리스트
- `ErrorType` enum에 새 값 추가
- `ErrorTypeTest.errorTypeProvider()`에 테스트 케이스 추가
- `ErrorTypeTest.enumConstantCount()`의 `hasSize(N)` 값을 N+1로 업데이트

### 4.5 커밋 메시지 컨벤션

**형식**: `{type}: {한국어 설명}`

| type | 용도 |
|------|------|
| `feat` | 새 기능 추가 |
| `fix` | 버그 수정 |
| `test` | 테스트 추가/수정 |
| `refactor` | 리팩토링 (기능 변경 없음) |
| `docs` | 문서 추가/수정 |
| `chore` | 빌드 설정, 의존성 관리 |
| `init` | 초기 설정 |

- 본문: 변경된 파일/클래스 목록을 `-` 리스트로 기술

### 4.6 에러 처리 패턴

모든 비즈니스 예외는 `CoreException` + `ErrorType` 조합으로 처리한다.

**흐름**: `throw CoreException(ErrorType.XXX)` → `GlobalExceptionHandler` → `ErrorResponse(code, message)`

**새 도메인 에러 추가 시**:
1. `ErrorType` enum에 에러 추가 (HttpStatus, code, message)
2. 도메인 코드에서 `throw new CoreException(ErrorType.XXX)`
3. `GlobalExceptionHandler`는 수정 불필요 (자동 처리)
4. `CoreException(ErrorType, String customMessage)` 생성자로 커스텀 메시지 전달 가능 (기본값: ErrorType 메시지 사용)

- `@Valid` 검증 실패 → `MethodArgumentNotValidException` → BAD_REQUEST 자동 반환

#### 인증 패턴
- 사용자 인증 헤더: `X-Loopers-LoginId`, `X-Loopers-LoginPw` → `HeaderValidator.validate()`
- 관리자 인증 헤더: `X-Loopers-Ldap` → `AdminHeaderValidator.validate()`
- Controller: `@RequestHeader(required = false)` → null 허용 → 단일 `UNAUTHORIZED` 응답 (보안: 실패 사유 미구분)
- 비밀번호 일치 검증은 `AuthenticationManager` 포트(현재 `CustomAuthenticationManager`)에 위임하고, 비밀번호 정책 검증은 도메인 VO(`Password.validatePasswordPolicy`)가 담당
- URL 패턴: 사용자 `/api/v1/{resource}`, 관리자 `/api-admin/v1/{resource}`

#### 낙관적 락 (`@Version`) 충돌 처리 패턴

`@Version` 기반 낙관적 락 충돌(`OptimisticLockingFailureException`) 처리 시, **`@Retryable`을 1순위로 사용**한다.

**1순위: `@Retryable` + `@Recover` (기본)**
- `@Retryable`은 `@Transactional` **바깥**에서 AOP 프록시로 감싸므로, TX 커밋 시점에 발생하는 `OptimisticLockingFailureException`을 자연스럽게 catch한다.
- 재시도 시 새 TX가 열려 DB에서 최신 version을 다시 읽으므로 정합성이 보장된다.
- 재시도 소진 시 `@Recover` 메서드에서 도메인 전용 `CoreException`으로 변환한다.

```java
// Facade — @Retryable이 @Transactional 바깥에서 감싸 TX 커밋 예외를 catch
@Retryable(
    retryFor = OptimisticLockingFailureException.class,
    recover = "recoverXxxConflict",
    maxAttempts = 3,
    backoff = @Backoff(delay = 50, multiplier = 2, maxDelay = 200, random = true)
)
@Transactional
public Result doSomething(...) { ... }

@Recover
public Result recoverXxxConflict(OptimisticLockingFailureException e, ...) {
    throw new CoreException(ErrorType.XXX_CONFLICT);
}
```

**2순위: try-catch + `saveAndFlush()` (예외적)**
- `@Retryable` 사용이 불가능한 경우에만 사용한다 (예: 이미 `@Retryable`이 적용된 메서드 내부에서 별도 낙관적 락 처리가 필요한 경우).
- `saveAndFlush()`로 즉시 SQL을 실행하여 try-catch 범위 내에서 예외를 잡는다.
- 이 패턴은 구조적 우회이므로, 반드시 주석으로 `@Retryable` 미사용 사유를 명시한다.

**사용 금지: try-catch + `save()` (flush 없이)**
- `save()`는 영속성 컨텍스트에 merge만 수행하고, 실제 SQL은 TX 커밋 시점에 실행된다.
- 따라서 Facade 내부의 try-catch로는 `OptimisticLockingFailureException`을 잡을 수 없다.

**참고 — 안전망**: `GlobalExceptionHandler`에 `OptimisticLockingFailureException` → 409 핸들러가 존재한다. `@Retryable`이나 Facade에서 잡히지 않는 경우 최종 방어선으로 동작한다.

### 4.7 도메인 모델 패턴

#### 팩토리 메서드
- `create(...)`: 새 객체 생성 (유효성 검증 포함, id = null)
- `reconstruct(...)`: DB에서 복원 (검증 생략, id 포함)
- 생성자는 `private`으로 제한

#### 유효성 검증 순서
null 체크 → empty 체크 → 길이 제한 → 포맷(정규식) → 비즈니스 규칙

#### 입력값 정규화 (Normalization)
- 신규 생성 입력은 `create()` 팩토리 메서드에서 정규화: `loginId` → `trim().toLowerCase()`, `name`/`email` → `trim()`
- 조회/인증 경로의 조회 키 생성 목적 정규화(`LoginId.normalize`)는 Service에서 1회 수행 가능
- 동일 요청 경로에서 중복 정규화는 금지

#### Value Object
- Java `record`로 구현 (예: `Password`)
- `create()` + `from()` / `fromEncoded()` 팩토리 메서드 패턴 적용
- `from()`: DB 복원 (검증 생략), `fromEncoded()`: 인코딩된 값 복원 (Password 등)
- 비즈니스 로직(검증, 변환)을 VO 내부에 캡슐화

#### BaseEntity 계층
- `BaseEntity`: 모든 엔티티의 공통 베이스 (`id`, `createdAt`, `updatedAt`)
  - `id`: `@GeneratedValue(IDENTITY)` → 엔티티에서 직접 id 설정 불가
  - `createdAt`, `updatedAt`: `@PrePersist`, `@PreUpdate`로 자동 관리
  - 상속 대상: Hard Delete(`LikeEntity`, `CartItemEntity`) / 삭제 불가(`OrderEntity`, `OrderItemEntity`, `IdempotencyKeyEntity`)
- `SoftDeleteBaseEntity extends BaseEntity`: Soft Delete 전용 (`deletedAt`, `delete()`, `restore()`)
  - 상속 대상: `UserEntity`, `BrandEntity`, `ProductEntity`

#### 필드 가변성
- 변경 가능 필드: `private` (non-final) → `changeXxx()` 메서드 제공 (예: `password`)
- 불변 필드: `private final` → 변경 불가 (예: `loginId`, `name`, `birthDate`, `email`)

#### 도메인 서비스 (Domain Service)
- **순수 Java 클래스** — Spring 어노테이션 없음 (Domain Model과 일관성 유지)
- **상태 없이(stateless)** 설계: 동일한 도메인 경계(BC) 내의 도메인 객체 협력을 중재
- 비즈니스 불변식(invariant) 검증에 사용 (예: 여러 도메인 객체 간 협력이 필요한 복합 검증)
- **Domain Service 사용 기준**: 여러 도메인 객체 간 협력 중재가 필요한 경우에만 사용. 단일 도메인 모델의 단순 검증(boolean 분기 등)은 Domain Model 메서드로 구현
- **점진적 분리**: 처음엔 Domain Model에 두고, 복잡도가 증가하면 Domain Service로 분리
- **Service가 필요한 데이터를 조회하여 DomainService에 전달한다.** DomainService는 Repository/Port를 직접 호출하지 않는다.
- 필요 시 `support/config/` 하위에 `@Configuration` + `@Bean`으로 등록

### 4.8 CQRS 레이어 흐름

#### 아키텍처 원칙
- 의존 방향: `Application → Domain ← Infrastructure`
- Domain Layer가 중심, Application과 Infrastructure가 Domain에 의존
- Repository Interface는 Domain Layer에 정의, 구현체는 Infrastructure에 위치

Controller → Facade → Service / Domain Service → Repository(interface) → RepositoryImpl → JpaRepository + Entity ↔ Domain

#### 레이어 규칙

##### @Transactional 사용 규칙
- **메서드 레벨에서만 사용** (DO): 각 메서드의 트랜잭션 범위를 명시적으로 선언
- **클래스 레벨 사용 금지** (Do Not): 암묵적 트랜잭션 적용 방지
- Command: `@Transactional`
- Query: `@Transactional(readOnly = true)`

##### 호출 순서 및 책임

1. **Facade는 Service만 호출한다.** Port, Repository, DomainService 등을 직접 호출하지 않는다.
2. **Service가 모든 외부 호출의 주체다.** Repository, Port(Cross-BC), DomainService를 Service에서 호출한다. Service는 Repository와 Port를 자유롭게 조합할 수 있으며, Port 호출만을 위한 별도 wrapper Service 생성은 지양한다.
3. **호출 순서**: Controller → Facade → Service → (Repository / Port / DomainService). 계층 건너뛰기 금지.
4. **DomainService는 Repository/Port를 호출하지 않는다.** Service가 데이터를 조회하여 DomainService에 전달한다.
5. **Domain Model은 순수 비즈니스 로직만 포함한다.** 외부 의존(Repository, Port, Spring 등) 없음.
6. **Service는 다른 Service를 호출하지 않는다.** Service의 의존 대상은 Repository, Port(Cross-BC), DomainService, EventPublisher로 자유롭게 조합할 수 있다. Port만 감싸는 thin wrapper Service를 만들지 않는다. 여러 Service 간 오케스트레이션은 Facade에서 수행한다.
7. **Service의 public 메서드는 유스케이스 계약과 Facade/EventListener가 조합하는 단계를 노출한다.** 클래스 내부 전용 helper는 `private`로 감춘다.

##### 비즈니스 로직 분리
- 비즈니스 로직(도메인 규칙, 검증, 계산)은 Domain Model 또는 Domain Service에서 작성한다.
- 서비스 로직(유스케이스 오케스트레이션, 트랜잭션 관리)은 Facade와 Service에서 작성한다.

##### Bounded Context 경계

| BC | 포함 도메인 | 설명 |
|----|-----------|------|
| `catalog` | Brand, Product | 상품 카탈로그 |
| `engagement` | ProductLike, BrandLike | 사용자 참여 |
| `cart` | CartItem | 장바구니 |
| `ordering` | Order, OrderItem | 주문 |
| `user` | User | 사용자 |

##### 같은 BC 내 통신
- Facade에서 같은 BC 내 다른 도메인의 Service를 직접 호출 가능
- 예: `ProductQueryFacade`에서 `BrandQueryService` 호출 (catalog BC 내)

##### 다른 BC 간 통신 (동기)
- Port 인터페이스 + ACL 구현체 패턴
- 인터페이스: `{domain}/application/port/out/client/{other-domain}/{OtherDomain}Port`
- 구현체: `{domain}/infrastructure/acl/{other-domain}/{OtherDomain}PortImpl`
- ACL 구현체는 **Provider BC의 Facade(또는 Facade 성격 전용 API)** 만 호출한다.
- ACL 구현체는 **thin adapter** 로 유지한다: 요청/응답 변환, 단순 위임만 담당.
- ACL 구현체에 비즈니스 로직/오케스트레이션 로직/에러 매핑을 두지 않는다.
- **에러 매핑은 ACL을 호출하는 Service에서 처리한다.** Provider 예외를 Consumer BC 예외로 변환.
- ACL 구현체에서 Provider BC의 `Service/Repository/JpaRepository/Querydsl/Entity` 직접 참조를 금지한다. (Provider **Facade**만 호출)
- 예: 주문 시 재고 차감 → `OrderStockManager` + `OrderStockManagerImpl` → Catalog `ProductCommandFacade.decreaseStock()` 위임

##### 다른 BC 간 통신 (비동기)
- 도메인 이벤트 + `@TransactionalEventListener`
- 최종적 일관성만 필요한 부수효과에 사용
- 예: 주문 완료 후 알림 발송, 통계 업데이트
- **흐름 추적 필수 규칙**: Domain Event 사용 시 아래 두 가지를 반드시 명시한다.
  1. **Event 클래스 Javadoc에 구독자 목록 명시**: `@subscriber {ListenerClass} - {역할}` 형식으로 모든 구독자를 기록
  2. **Publisher 쪽 주석에 파생 효과 명시**: 이벤트 발행 라인에 `→ [{Listener}] {효과}` 형식으로 어떤 리스너가 반응하는지 인라인 주석 기록

| 레이어 | 클래스 | 어노테이션 | 역할 |
|--------|--------|-----------|------|
| Controller | `{Domain}Controller` / `{Domain}CommandController` | `@RestController` | 사용자 요청 수신 (`/api/v1/`), Facade 호출 |
| Controller | `{Domain}AdminCommandController` / `{Domain}AdminQueryController` | `@RestController` | 관리자 요청 수신 (`/api-admin/v1/`), Facade 호출 |
| Facade | `{Domain}CommandFacade` | `@Service`, method-level `@Transactional` | 명령 유스케이스 오케스트레이션, 트랜잭션 경계, **Service만 호출** |
| Facade | `{Domain}QueryFacade` | `@Service`, method-level `@Transactional(readOnly = true)` | 조회 유스케이스 오케스트레이션 |
| Service | `{Domain}CommandService` | `@Service`, method-level `@Transactional` | 단일 도메인 비즈니스 로직 실행 **(다른 Service 호출 금지, public은 유스케이스 계약만 노출, Repository와 Port 자유 조합 가능, Port만 감싸는 wrapper Service 금지)** |
| Domain Service | `{Domain}XxxValidator` 등 | (순수 Java, `@Bean` 등록) | 비즈니스 불변식 검증 **(Repository/Port 호출 금지, Service가 데이터 전달)** |
| Repository(I) | `{Domain}Command/QueryRepository` | (인터페이스, `domain/repository/`) | 명령(save,delete) / 조회(find,exists) 계약 |
| RepositoryImpl | `{Domain}Command/QueryRepositoryImpl` | `@Repository` | Entity ↔ Domain 변환 후 JPA 호출 |
| ACL Adapter | `{OtherDomain}PortImpl` | `@Component` | Cross-BC 동기 통신 어댑터. **Provider Facade(또는 Facade 성격 전용 API) 호출만 허용, 변환/위임만 수행 (에러 매핑 금지)** |
| Entity | `{Domain}Entity` | `@Entity` | `of(...)` 팩토리 메서드 |
| EntityMapper | `{Domain}EntityMapper` | `@Component` | `toEntity(Domain)` + `toDomain(Entity)` 변환 |
| QueryPort(I) | `{Domain}QueryPort` | (인터페이스, `application/port/out/query/`) | 유스케이스 전용 복잡 조회 계약 |
| QueryPortImpl | `{Domain}QueryPortImpl` | `@Repository` | QueryPort 구현 (QuerydslRepository에 위임) |
| QuerydslRepository | `{Domain}QuerydslRepository` | `@Repository` | QueryDSL 쿼리 로직 구현 (JPAQueryFactory 사용) |

#### 조회 방식 판단 가이드

| 기준 | Domain Repository | QueryPort |
|------|-------------------|-----------|
| 위치 | `domain/repository/` | `application/port/out/query/` |
| 반환 타입 | Domain Model | DTO (OutDto, Projection) |
| 시그니처 | 도메인 언어만 (Spring/JPA 타입 금지) | 유스케이스 DTO, criteria 사용 가능 |
| 용도 | 단일 엔티티 CRUD, 존재 여부 확인 | 복잡 조회, 다중 조인, 집계, Projection |
| 페이지네이션 | `PageCriteria`/`PageResult` (domain/repository/vo/) | `PageCriteria`/`PageResult` 또는 Spring Page 래핑 |

#### QueryDSL 사용 기준
- **Domain Repository**: Spring Data 파생 쿼리 사용 (단순 WHERE 조건, 고정 필터)
- **QueryPort**: QueryDSL 사용 (DTO Projection, 동적 필터, 다중 조인, 집계)
- QueryDSL 쿼리 로직은 `infrastructure/querydsl/{Domain}QuerydslRepository`에 구현, `QueryPortImpl`은 thin adapter로 위임
- QueryDSL 의존성 및 Q-class는 이미 설정 완료 (`modules/jpa/build.gradle.kts`)

#### 도메인 리포지토리 규칙

- **MUST**: 시그니처에 도메인 언어만 사용 (`User`, `Long`, `String`, `Optional<User>`, `List<User>`, `PageCriteria`, `PageResult`)
- **MUST NOT**: `Page`, `Pageable`, `Slice`, `Sort`, `Specification`, `Predicate` 등 Spring/JPA 타입 노출 금지
- **MUST NOT**: 유스케이스 DTO (OutDto 등) 반환 금지 — Domain Model만 반환
- Infrastructure 구현체에서 Spring 타입 ↔ 도메인 타입 변환 담당

#### 유스케이스 전용 조회 (QueryPort)

복잡 조회, Projection, DTO 직접 반환이 필요한 경우 QueryPort를 사용한다.

- 인터페이스: `{domain}/application/port/out/query/{Domain}QueryPort`
- 조건 객체: `{domain}/application/port/out/query/criteria/{Domain}SearchCriteria`
- QueryDSL 쿼리: `{domain}/infrastructure/querydsl/{Domain}QuerydslRepository`
- 구현체: `{domain}/infrastructure/query/{Domain}QueryPortImpl` (QuerydslRepository에 위임)
- **"Repository" 네이밍 사용 금지** — 반드시 `QueryPort` 사용 (단, QuerydslRepository는 예외)
- Service에서 QueryPort 인터페이스를 통해 호출

#### Port 네이밍 규칙

| 유형 | 위치 | 네이밍 | 예시 |
|------|------|--------|------|
| Cross-BC (client) | `application/port/out/client/` | 역할 서술형 네이밍 | `LikeTargetValidator`, `CartProductReader`, `OrderStockManager` |
| 유스케이스 조회 (query) | `application/port/out/query/` | `...QueryPort` | `ProductQueryPort` |
| 유틸리티 (util) | `application/port/out/util/` | 기존 네이밍 유지 (`...Port` 미사용) | `PasswordEncoder` |

#### Pagination VO (`domain/repository/vo/`)

- `PageCriteria`: 프레임워크 비의존 record (`page`, `size`)
- `PageResult<T>`: 프레임워크 비의존 record (`content`, `page`, `size`, `totalElements`)
- 위치: `domain/repository/vo/` — Domain Layer에 속함
- Domain Repository와 QueryPort 모두 사용 가능
- Infrastructure 구현체에서 Spring `Page`/`Pageable` ↔ `PageCriteria`/`PageResult` 변환 담당

#### Entity 업데이트 패턴
- **신규 생성**: `mapper.toEntity(domain)` → `jpaRepository.save(entity)` → `mapper.toDomain(entity)`
- **기존 수정**: `mapper.toEntity(domain)` → `jpaRepository.save(entity)` → `mapper.toDomain(entity)` (id가 설정되어 있으면 JPA merge 수행)
- ⚠️ Entity.of(id, ...)는 id를 포함하여 생성할 수 있으므로, 업데이트 시에도 동일하게 save() 호출

### 4.9 DTO 패턴

모든 DTO는 Java `record`로 구현한다.

| DTO 유형 | 위치 | 변환 메서드 |
|----------|------|-----------|
| Request | `interfaces/web/request/` | 순수 데이터 + validation (변환 없음) |
| InDto | `application/dto/in/` | 불변 record, 변환 없음 |
| OutDto | `application/dto/out/` | `from(Domain)` static 팩토리 |
| Response | `interfaces/web/response/` | `from(OutDto)` static 팩토리 |
| Entity | `infrastructure/entity/` | `of(...)` 팩토리 메서드 |
| EntityMapper | `infrastructure/mapper/` | `toEntity(Domain)` + `toDomain(Entity)` |

#### 데이터 변환 흐름
`Request` → **Controller에서 InDto 직접 생성** → **Facade** → Domain → `OutDto.from(domain)` → **Controller** → `Response.from(outDto)`

- Response에서 데이터 마스킹 가능 (예: `UserMeResponse.maskName()` — 이름 마지막 글자 마스킹)

- Request에 `@NotBlank`, `@NotNull` 등 Jakarta Validation 적용
- Response에 민감정보(password) 포함 금지
- ⚠️ Controller에서 `Map<String, Object>` 반환 금지 — 페이지네이션 포함 모든 응답은 전용 Response record 사용
  - 페이지네이션 응답: `{Domain}PageResponse` record + `from({Domain}PageOutDto)` 팩토리 메서드

#### 관리자/사용자 DTO 분리
- 관리자 전용 DTO: `Admin` 접두사 (예: `AdminBrandCreateInDto`, `AdminBrandOutDto`, `AdminBrandCreateRequest`)
- 사용자 전용 DTO: 접두사 없음 (예: `BrandOutDto`, `BrandResponse`)
- 관리자/사용자 응답 필드가 다를 수 있으므로 별도 DTO 정의 (예: 관리자는 `deletedAt` 포함)

## 5. 주의사항

### Never Do
- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용한 구현 금지
- null-safety 하지 않게 코드 작성 금지 (Java의 경우, Optional 활용)
- println 코드 남기지 말 것

### Recommendation
- 실제 API를 호출해 확인하는 E2E 테스트 코드 작성
- 재사용 가능한 객체 설계
- 성능 최적화에 대한 대안 및 제안
- 개발 완료된 API의 경우, `.http/**.http`에 분류해 작성

### Priority
1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴 분석 후 일관성 유지
