# 핵심 규칙 반영 — 문서/스킬/플러그인 수정 계획

## Context

체크리스트 기반 핵심 규칙이 추가되었으며, 개발자와의 논의를 통해 다음 6가지가 확정됨:

1. **Repository Interface → ALL Domain Layer** (`domain/repository/`)
2. **테스트 더블 → 모든 종류** (Fake/Stub/Mock/Spy) 상황에 맞게 사용
3. **DomainService → 현행 유지** (Repository 호출 금지, Service가 데이터 전달)
4. **패키지 구조 → 현행 유지** + Repository Interface만 `domain/repository/`로 이동
5. **BC(Bounded Context) 경계 도입** (문서에만 반영, 코드 재구성은 별도 지시 시)
   - `catalog` BC: Brand, Product (같은 BC → Service 직접 호출 가능)
   - `engagement` BC: Like (다른 BC → Client/ACL로 접근)
   - `ordering` BC: Order, OrderItem (다른 BC → Client/ACL로 접근)
   - `user` BC: User (다른 BC → Client/ACL로 접근)
6. **Cross-BC 통신 패턴**
   - 인터페이스: `application/port/out/client/{domain}/{Domain}Port.java`
   - 구현체: `infrastructure/acl/{domain}/{Domain}PortImpl.java`
   - 동기(예외 필요): Port + ACL (재고 차감, 포인트 차감 등)
   - 비동기(최종적 일관성): Event (`@TransactionalEventListener`)

추가 반영 사항:
- 아키텍처 원칙: `Application → Domain ← Infrastructure` (의존성 역전)
- 도메인 서비스: 상태 없이(stateless), 동일 BC 내 도메인 객체 협력 중심
- **도메인 리포지토리 순수성**: 시그니처에 도메인 언어만 사용, Spring/JPA 타입 노출 금지
- **유스케이스 전용 QueryPort 패턴**: 복잡 조회를 Application Layer 포트로 분리
- **Port 패키지 구조 통합**: `application/port/out/` 하위에 `client/`, `query/`, `util/` 배치
- **Pagination VO**: `domain/repository/vo/`에 PageCriteria, PageResult 정의

---

## 변경 대상 파일 및 수정 내용

### 1. CLAUDE.md (`/Users/dhwan/Dev/loop-pack-be-l2-vol3-java/CLAUDE.md`)

#### 1-1. Section 3 패키지 구조 (line 51~85)
- `application/client/` → `application/port/out/client/` (port 하위로 이동)
- `application/port/out/query/` 신규 추가
- `application/port/out/util/` 신규 추가
- `domain/repository/vo/` 신규 추가 (PageCriteria, PageResult)
- `infrastructure/query/` 신규 추가
- `infrastructure/acl/` 구현체 네이밍: `...ClientImpl` → `...PortImpl`

**After (패키지 구조 전체):**
```
{domain}/
├── application/
│   ├── service/
│   ├── facade/
│   ├── port/
│   │   └── out/
│   │       ├── client/                        # Cross-BC 포트
│   │       │   └── {other-domain}/
│   │       │       └── {OtherDomain}Port
│   │       ├── query/                         # 유스케이스 전용 조회 포트
│   │       │   ├── {Domain}QueryPort
│   │       │   └── criteria/
│   │       │       └── {Domain}SearchCriteria
│   │       └── util/                          # 유틸리티 포트 (기존 네이밍 유지)
│   │           ├── PasswordEncoder
│   │           └── AuthenticationManager
│   └── dto/
│       ├── in/
│       └── out/
├── domain/
│   ├── model/ + enum/ + vo/
│   ├── repository/                            # 도메인 리포지토리 인터페이스 (CQRS)
│   │   ├── {Domain}CommandRepository
│   │   ├── {Domain}QueryRepository
│   │   └── vo/                                # 리포지토리 계약 VO
│   │       ├── PageCriteria
│   │       └── PageResult<T>
│   ├── event/
│   └── service/
├── infrastructure/
│   ├── jpa/
│   ├── repository/                            # 도메인 리포지토리 구현체
│   ├── acl/                                   # Cross-BC 포트 구현체
│   │   └── {other-domain}/
│   │       └── {OtherDomain}PortImpl
│   ├── query/                                 # QueryPort 구현체
│   │   └── {Domain}QueryPortImpl
│   └── entity/
├── interfaces/
│   ├── controller/ + request/ + response/
│   └── event/
└── support/
    ├── common/ + error/
    └── config/
```

#### 1-2. Section 4.3 단위 테스트 Mock 패턴 (line 151~155)
- 제목: "단위 테스트 Mock 패턴" → "단위 테스트 패턴"
- 첫 줄 추가: "모든 테스트 더블(Fake, Stub, Mock, Spy) 사용 가능 — 상황에 맞게 적절한 것을 선택"
- 기존 Mockito 설명은 "Mock 사용 시:" 접두어 붙여서 유지

#### 1-3. Section 4.6 도메인 서비스 (line 226~230)
- 기존 규칙 아래에 추가:
  - `- **상태 없이(stateless)** 설계: 동일한 도메인 경계(BC) 내의 도메인 객체 협력을 중재`

#### 1-4. Section 4.7 CQRS 레이어 흐름 (line 232~)
- 섹션 시작 부분에 아키텍처 원칙 추가:
```
#### 아키텍처 원칙
- 의존 방향: `Application → Domain ← Infrastructure`
- Domain Layer가 중심, Application과 Infrastructure가 Domain에 의존
- Repository Interface는 Domain Layer에 정의, 구현체는 Infrastructure에 위치
```
- 레이어 테이블: Repository (I) 위치를 `domain/repository/` 반영
- **레이어 테이블에 QueryPort 행 추가:**

| 레이어 | 클래스 | 위치 | 역할 |
|--------|--------|------|------|
| QueryPort(I) | `{Domain}QueryPort` | `application/port/out/query/` | 유스케이스 전용 복잡 조회 계약 |
| QueryPortImpl | `{Domain}QueryPortImpl` | `infrastructure/query/` | QueryPort 구현 (JPA/QueryDSL) |

#### 1-5. Section 4.7 Cross-BC 통신 (line 250~253)
- 기존 Cross-BC 규칙을 대폭 확장:
- **네이밍 업데이트**: `Client` → `Port`, `ClientImpl` → `PortImpl`
- **위치 업데이트**: `application/client/` → `application/port/out/client/`

```
#### Bounded Context 경계

| BC | 포함 도메인 | 설명 |
|----|-----------|------|
| `catalog` | Brand, Product | 상품 카탈로그 |
| `engagement` | Like | 사용자 참여 |
| `ordering` | Order, OrderItem | 주문 |
| `user` | User | 사용자 |

##### 같은 BC 내 통신
- Facade에서 같은 BC 내 다른 도메인의 Service를 직접 호출 가능
- 예: `ProductQueryFacade`에서 `BrandQueryService` 호출 (catalog BC 내)

##### 다른 BC 간 통신 (동기)
- Port 인터페이스 + ACL 구현체 패턴
- 인터페이스: `{domain}/application/port/out/client/{other-domain}/{OtherDomain}Port`
- 구현체: `{domain}/infrastructure/acl/{other-domain}/{OtherDomain}PortImpl`
- 구현체에서만 다른 도메인의 domain model, JPA 직접 참조 허용
- 예: 주문 시 재고 차감 → `ProductStockPort` + `ProductStockPortImpl`

##### 다른 BC 간 통신 (비동기)
- 도메인 이벤트 + `@TransactionalEventListener`
- 최종적 일관성만 필요한 부수효과에 사용
- 예: 주문 완료 후 알림 발송, 통계 업데이트
```

#### 1-6. 신규 섹션: 조회 방식 판단 가이드 (Section 4.7에 추가)

```
#### 조회 방식 판단 가이드

##### Repository (Domain Layer)
- **소유**: Domain Layer가 소유하는 저장소 계약
- **반환**: Domain Model 또는 그 집합만 반환
- **목적**: 핵심 업무 규칙(비즈니스 로직)을 수행하기 위해 Domain Model의 상태를 재구성/영속화
- **판단 질문**: "도메인 로직(행위)을 실행하기 위해 Domain Model 객체 자체가 필요한가?"

✅ 권장: 주문 취소 요청 → `OrderRepository.findById(orderId)`로 Order Domain Model 조회
  → `order.cancel()` 비즈니스 로직 실행 → 저장
  (핵심 업무 규칙은 Domain Model 내부에 위치, 영속성 세부사항으로부터 격리)

❌ 안티패턴: `OrderRepository.findAllSummary()` → 화면용 `OrderSummaryDTO` 반환
  (Domain Layer가 UI 요구사항에 의존 → 의존성 규칙 위반)

##### QueryPort (Application Layer)
- **소유**: Application Layer가 소유하는 유스케이스 특화 조회 계약
- **반환**: DTO 또는 프레임워크 비의존 조회 전용 모델
- **목적**: 비즈니스 로직 없이 화면 표시나 조회 성능 최적화(Join, Projection)
- **판단 질문**: "비즈니스 로직 없이 특정 유스케이스/UI에 최적화된 데이터가 필요한가?"

✅ 권장: 마이페이지 주문 내역 → `MyOrderQueryPort.findMyOrders(userId)`
  → 인프라 구현체에서 DB 최적화 조회 → `OrderHistoryDTO` 직접 반환
  (Domain Model 행위 불필요, DTO 직접 조회가 아키텍처·성능 면에서 유리)

❌ 안티패턴: UserRepository로 무거운 Domain Model 전체 조회 → 이름/이메일만 추출해 Controller에 전달
  (Domain Model이 외부에 직접 노출, 보안·유지보수 측면에서 결합도 증가)

##### 판단 트리
1. 조회 후 Domain Model의 메서드(행위)를 실행해야 하는가?
   → YES: **Repository** 사용 (Domain Model 반환, Domain Layer 위치)
2. 단순히 데이터를 가공하여 화면에 표시하거나 성능 최적화(Projection)가 중요한가?
   → YES: **QueryPort** 사용 (DTO 반환, Application Layer 위치)
```

#### 1-7. 신규 섹션: 도메인 리포지토리 순수성 규칙 (Section 4.7에 추가)

```
#### 도메인 리포지토리 규칙

##### MUST
- 시그니처(메서드/입력/반환)는 도메인 언어(Id, VO, Domain Model, PageCriteria, PageResult)만 사용
- Domain Layer에 정의 (`domain/repository/`)

##### MUST NOT
- Page, Pageable, JPA/QueryDSL 타입 노출 금지
- 유스케이스 응답 DTO(OutDto 등) 반환 금지
- Spring 프레임워크 타입 노출 금지
```

#### 1-8. 신규 섹션: 유스케이스 전용 조회 (QueryPort) 패턴

```
#### 유스케이스 전용 조회 (QueryPort)

- 복잡 조회, projection, 응답 DTO 반환이 필요한 조회는 QueryPort로 분리
- 인터페이스: `application/port/out/query/{Domain}QueryPort`
- 구현체: `infrastructure/query/{Domain}QueryPortImpl`
- "Repository" 네이밍 절대 사용 금지
- 유스케이스 DTO, PageResult<T> 반환 가능
- 쿼리 조건: 프레임워크 비의존 조건 객체 사용 (API DTO 직접 사용 금지)
- 조건 객체 위치: `application/port/out/query/criteria/`
- 조건 객체 네이밍: `{Domain}SearchCriteria` (e.g., OrderSearchCriteria)
```

#### 1-9. 신규 섹션: Port 네이밍 규칙

```
#### Port 네이밍 규칙

##### client/, query/ 포트
- 접미사: `...Port` 통일 (e.g., ProductStockPort, OrderQueryPort)
- 벤더/프레임워크 이름 금지 (Spring, BCrypt 등은 구현체에서만 사용)
- 역할이 드러나게 명명

##### util/ 포트
- `...Port` 접미사 사용하지 않음
- 기존 네이밍 유지 (e.g., PasswordEncoder, AuthenticationManager)
- 인터페이스: `application/port/out/util/`
- 구현체: `{domain}/support/common/util/` 또는 `global/common/{핵심기능}/`
- **분류 기준**: 비즈니스 의미 없는 기술적 유틸리티만 허용 (암호화, 인증 검증 등)
- 외부 BC 협력 목적 → 무조건 `client/`, 유스케이스 조회 목적 → 무조건 `query/`
```

#### 1-10. 신규 섹션: Pagination VO (도메인 모델 패턴 또는 CQRS 섹션에 추가)

```
#### Pagination VO (`domain/repository/vo/`)

##### 근거
- Repository 인터페이스는 Domain Layer에 정의되며, 도메인이 데이터 접근 계약을 소유한다
- 페이지네이션 파라미터(page, size, sort)는 이 계약의 일부로, 도메인이 인프라 구현 세부사항(Spring Page/Pageable)에 의존하지 않으면서도 데이터 접근 방식을 정의할 수 있어야 한다
- 따라서 프레임워크 비의존 VO(PageCriteria, PageResult)를 도메인 레이어에 두어, 도메인이 자체 언어로 계약을 표현한다

##### 정의
- PageCriteria: 프레임워크 비의존 record (page, size, sort)
- PageResult<T>: 프레임워크 비의존 record (content, page, size, totalElements)
- 위치: `domain/repository/vo/`
- Domain Repository와 QueryPort 모두 사용 가능
- Infrastructure 구현체에서 Spring Page/Pageable ↔ PageCriteria/PageResult 변환 담당
```

---

### 2. layered-architecture/SKILL.md

#### 2-1. 레이어 흐름도 (line 10~23)
- `Repository Interface (application/repository/)` → `Repository Interface (domain/repository/)`

#### 2-2. Section 3 핵심 규칙 (line 39~)
- 아키텍처 원칙 추가: `Application → Domain ← Infrastructure`
- BC 규칙 추가: 같은 BC 직접 호출, 다른 BC → Port/ACL
- **Port 구조 추가**: `application/port/out/` 하위 구성 설명
- **QueryPort 패턴 추가**: 유스케이스 전용 조회 계약 설명
- **Domain Repository 순수성 규칙 추가**

#### 2-3. Section 6 패키지 구조 (line 104~127)
- `application/` 하위: `client/` 제거, `port/out/client/`, `port/out/query/`, `port/out/util/` 추가
- `domain/` 하위: `repository/vo/` 추가
- `infrastructure/` 하위: `acl/` 네이밍 업데이트 (`PortImpl`), `query/` 추가

---

### 3. domain-model/SKILL.md

#### 3-1. Section 6 도메인 서비스 (line 78~101)
- 추가: "**상태 없이(stateless)** 설계: 동일 BC 내 도메인 객체 협력을 중재"
- 추가: "복합 유스케이스(cross-BC 조합)는 Application Layer(Facade)에서 Port를 통해 처리"

#### 3-2. Pagination VO 추가
- `domain/repository/vo/`에 PageCriteria, PageResult 설명 추가
- 도메인 모델과 함께 도메인 레이어의 계약 VO로서의 역할 명시

---

### 4. create-endpoint/SKILL.md

#### 4-1. Section 2.2 Repository (line 31~39)
- Repository Interface 위치를 `domain/repository/`로 명시
- 주석 추가: `# 위치: domain/repository/`

#### 4-2. Cross-BC 필요 시 체크리스트 추가
- 다른 BC 데이터 필요 시 Port 인터페이스 + ACL 구현체 생성 단계 추가

#### 4-3. QueryPort 필요 시 생성 단계 추가
- 복잡 조회/Projection 필요 시 QueryPort 인터페이스 + 구현체 생성 단계 추가
- Port 네이밍 규칙 반영 (`...Port` 접미사)

---

### 5. tdd-workflow/SKILL.md

#### 5-1. Section 4.1 단위 테스트 (line 122~127)
- "Mock 프레임워크 사용" → "모든 테스트 더블(Fake, Stub, Mock, Spy) 사용 가능"
- "Mock 사용 시:" 접두어로 기존 Mockito 설명 유지

#### 5-2. QueryPort 테스트 패턴 추가
- QueryPort 단위 테스트: Mock으로 QueryPort 인터페이스를 stub
- QueryPortImpl 통합 테스트: TestContainers + 실제 DB 조회 검증

---

### 6. feature-dev 플러그인 파일들

#### 6-1. feature-dev.md (commands)
- 아키텍처 요약:
  - `Application → Domain ← Infrastructure` 추가
  - Repository 위치 `domain/repository/`로 변경
  - BC 경계 + Cross-BC 규칙 (Port/ACL, Event) 추가
  - **Port 구조, QueryPort 패턴, Repository 순수성 규칙 반영**

#### 6-2. code-explorer.md (agents)
- Package Structure: `application/client/` → `application/port/out/client/`
- `application/port/out/query/`, `infrastructure/query/` 추가
- 테스트 패턴: 모든 테스트 더블 반영
- BC 경계 인지 추가

#### 6-3. code-architect.md (agents)
- Layer Rules: Repository Interface → `domain/repository/` 검증
- BC Rules 추가: 다른 BC 간 Port/ACL 사용, 같은 BC 직접 호출 검증
- **Port Rules 추가**: Port 네이밍, QueryPort 분리 기준 검증
- **Repository 순수성 검증**: 도메인 리포지토리 시그니처에 Spring/JPA 타입 없는지 확인
- Test Rules: 모든 테스트 더블 허용

#### 6-4. code-reviewer.md (agents)
- CQRS Layer Violations: Repository Interface 위치 검증
- BC Violations 추가: Cross-BC 직접 참조 검증 (Port/ACL 없이 다른 BC Service 호출 금지)
- **Port Violations 추가**: QueryPort에 "Repository" 네이밍 사용 금지, Domain Repository에 Spring 타입 노출 금지
- Test Violations: 모든 테스트 더블 허용

---

## 변경하지 않는 파일

| 파일 | 이유 |
|------|------|
| error-handling/SKILL.md | 핵심 규칙 변경과 무관 |
| commit-protocol/SKILL.md | 커밋 규약 변경 없음 |
| comment-style/SKILL.md | 주석 컨벤션 변경 없음 |
| git-worktree/SKILL.md | 워크트리 관리 변경 없음 |
| requirements-analysis/SKILL.md | 요구사항 분석 프로세스 변경 없음 |
| plugin.json | 메타데이터 변경 불필요 |

---

## 검증 방법

1. Grep으로 `application/repository` 문자열이 수정 대상 파일에 남아있지 않은지 확인
2. `domain/repository` 내용이 모든 관련 파일에 일관 반영 확인
3. 테스트 더블 표현이 "Mock 전용"에서 "모든 테스트 더블"로 일관 변경 확인
4. BC 경계 정의(catalog/engagement/ordering/user)가 CLAUDE.md, layered-architecture, code-architect, code-reviewer에 반영 확인
5. Cross-BC 규칙(Port/ACL 패턴)이 CLAUDE.md, layered-architecture, create-endpoint, feature-dev 관련 파일에 반영 확인
6. `application/client/` 경로가 `application/port/out/client/`로 일관 변경 확인
7. Port 네이밍이 `...Port` 접미사로 통일 확인 (util/ 제외)
8. Domain Repository 시그니처에 Spring/JPA 타입이 없는지 확인
9. QueryPort에 "Repository" 네이밍이 사용되지 않았는지 확인
10. PageCriteria/PageResult가 `domain/repository/vo/`에 위치 확인
