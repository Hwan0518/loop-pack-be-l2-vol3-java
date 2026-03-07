---
name: layered-architecture
description: Layered architecture guide (including CQRS). Use when creating new domain packages, designing inter-layer data flows, or understanding layer structure of existing code.
---

# Layered Architecture

## 1. Layer Flow Diagram

```
Client Request
  → Controller (interfaces/)
    → Facade (application/facade/)
      → Service (application/service/)
        → Repository Interface (domain/repository/)
          → RepositoryImpl (infrastructure/repository/)
            → JPA Repository (infrastructure/jpa/)
              → Entity (infrastructure/entity/) ↔ Domain Model
        → Port (application/port/out/client/)
          → ACL Adapter (infrastructure/acl/)
            → Provider Facade (other BC)
        → Domain Service (domain/service/)
        → Domain Model (domain/model/)
  → Controller
→ Client Response
```

## 2. Layer Responsibilities

| Layer | Class Pattern | Annotation | Role |
|-------|--------------|-----------|------|
| Controller | `{Domain}Controller` | `@RestController` | Receive requests, return responses, call Facade |
| Facade (Command) | `{Domain}CommandFacade` | `@Service`, method-level `@Transactional` | Command use-case orchestration, transaction boundary |
| Facade (Query) | `{Domain}QueryFacade` | `@Service`, method-level `@Transactional(readOnly=true)` | Query use-case orchestration |
| Service | `{Domain}CommandService` | `@Service`, method-level `@Transactional` | Single domain business logic execution **(MUST NOT call other Services)** |
| ACL Adapter | `{OtherDomain}PortImpl` | `@Component` | Cross-BC sync adapter. **Only call provider Facade**, do mapping/delegation only (error mapping must be handled in caller Service) |
| Domain Service | `{Domain}XxxValidator`, etc. | Pure class (`@Bean` registration) | Stateless, mediates domain object collaboration within same BC, business invariant verification |
| Repository (I) | `{Domain}CommandRepository` | Interface | Command contract (save, delete) |
| Repository (I) | `{Domain}QueryRepository` | Interface | Query contract (find, exists) |
| RepositoryImpl | `{Domain}Command/QueryRepositoryImpl` | `@Repository` | Entity ↔ Domain conversion then JPA call. **데이터 반환만 담당, 비즈니스 예외 발생 및 `DataIntegrityViolationException` try-catch 금지** |
| Entity | `{Domain}Entity` | `@Entity` | `of(...)` 팩토리 메서드 |
| EntityMapper | `{Domain}EntityMapper` | `@Component` | `toEntity(Domain)` + `toDomain(Entity)` 변환 |
| QueryPort (I) | `{Domain}QueryPort` | Interface (`application/port/out/query/`) | Use-case specific complex query contract |
| QueryPortImpl | `{Domain}QueryPortImpl` | `@Repository` | QueryPort implementation (JPA/QueryDSL) |

## 3. Core Rules

### 3.0 Architecture Principle

- Dependency direction: `Application → Domain ← Infrastructure`
- Domain Layer is central; Application and Infrastructure depend on Domain
- Repository Interface defined in Domain Layer (`domain/repository/`), implementations in Infrastructure

### 3.1 Call Order

```
Controller → Facade → Service → Repository
```

- **Absolutely no layer skipping** (e.g., Controller → Service direct call prohibited)
- Controller must access business logic only through Facade

### 3.1.1 Service Dependency Rules

- **Service MUST NOT call other Services** (including QueryService, CommandService within same/different domain)
- Service dependency targets limited to: Repository, Port (Cross-BC), DomainService, EventPublisher
- **Service는 Repository와 Port를 자유롭게 조합할 수 있다. Port 호출만을 위한 별도 wrapper Service 생성은 금지한다.**
- Cross-Service orchestration is Facade's responsibility
- Service public methods should expose use-case contracts and reusable orchestration steps for Facade/EventListener; class-internal helpers should be `private`

### 3.2 Business vs Service Logic Separation

| Category | Location | Examples |
|----------|----------|---------|
| Business logic | Domain Model, Domain Service | Validation, domain calculations, state transitions, invariant verification |
| Service logic | Facade, Service | Use-case orchestration, transaction management, external system integration |

### 3.3 CQRS Separation

- Command: state changes (save, update, delete)
- Query: state queries (find, exists, count)
- Repository interfaces split into Command/Query from the start

### 3.4 Bounded Context Boundaries

| BC | Included Domains | Description |
|----|-----------------|-------------|
| `catalog` | Brand, Product | Product catalog |
| `engagement` | ProductLike, BrandLike | User engagement |
| `cart` | CartItem | Cart |
| `ordering` | Order, OrderItem | Orders |
| `user` | User | Users |

- **Same BC**: Facade can directly call another domain's Service within the same BC
- **Cross-BC (sync)**: Port interface (`application/port/out/client/`) + ACL implementation (`infrastructure/acl/`) pattern
- ACL implementation must call **provider Facade (or facade-style dedicated API)** only
- ACL implementation must stay thin (mapping/delegation only), with no business/orchestration/error-mapping logic
- Error mapping must be handled in the Service that calls the ACL
- ACL must not directly call provider `Service/Repository/JPA/QueryDSL/Entity`
- **Provider Facade Cross-BC 전용 메서드**: ACL에서 필요한 기능이 Provider Facade에 없으면, Provider Facade에 Cross-BC 전용 메서드를 추가한다. Javadoc 번호 목록에 포함하고, 메서드 주석에 `(Cross-BC 전용 — ACL에서 호출)` 표기
- **Cross-BC (async)**: Domain events + `@TransactionalEventListener` (eventual consistency)
- **Domain Event 흐름 추적 필수 규칙**:
  1. Event 클래스 Javadoc에 `@subscriber {ListenerClass} - {역할}` 형식으로 모든 구독자를 명시
  2. Publisher 쪽 이벤트 발행 라인에 `→ [{Listener}] {효과}` 형식으로 파생 효과를 인라인 주석으로 명시

### 3.5 Domain Repository Rules

- **MUST**: Signatures use only domain language (`User`, `Long`, `Optional<User>`, `List<User>`, `PageCriteria`, `PageResult`)
- **MUST NOT**: No `Page`, `Pageable`, `Slice`, `Sort`, `Specification` or other Spring/JPA type exposure
- **MUST NOT**: No use-case DTO (OutDto, etc.) returns — only Domain Model
- Infrastructure implementations handle Spring type ↔ domain type conversion

### 3.6 Use-Case Specific Query (QueryPort)

Use QueryPort when complex queries, Projections, or direct DTO returns are needed.

- Interface: `{domain}/application/port/out/query/{Domain}QueryPort`
- Criteria: `{domain}/application/port/out/query/criteria/{Domain}SearchCriteria`
- QueryDSL queries: `{domain}/infrastructure/querydsl/{Domain}QuerydslRepository`
- Implementation: `{domain}/infrastructure/query/{Domain}QueryPortImpl` (delegates to QuerydslRepository)
- **"Repository" naming prohibited** — must use `QueryPort` (except QuerydslRepository)
- Service calls through QueryPort interface

### 3.7 Port Naming Rules

| Type | Location | Naming |
|------|----------|--------|
| Cross-BC (client) | `application/port/out/client/` | Descriptive naming (`LikeTargetValidator`, `CartProductReader`, etc.) |
| Use-case query | `application/port/out/query/` | `...QueryPort` |
| Utility (util) | `application/port/out/util/` | Keep existing naming (`...Port` suffix not used) |

## 4. Data Conversion Flow (DTO Pattern)

```
Request → [Controller] create InDto → [Facade] → Domain → OutDto.from(domain) → [Controller] → Response.from(outDto)
```

### 4.1 DTO Type Rules

| DTO | Location | Conversion Method | Role |
|-----|----------|------------------|------|
| Request | `interfaces/web/request/` | None (Controller maps explicitly) | Receive external input, apply Jakarta Validation |
| InDto | `application/dto/in/` | None (immutable record) | Pass input between layers |
| OutDto | `application/dto/out/` | `from(Domain)` static factory | Domain → output conversion |
| Response | `interfaces/web/response/` | `from(OutDto)` static factory | Final response (masking possible) |

### 4.2 DTO Rules

- All DTOs implemented as immutable records
- Apply `@NotBlank`, `@NotNull`, etc. Jakarta Validation to Request
- **Prohibited**: including sensitive info (password, etc.) in Response
- Data masking possible in Response (e.g., last character of name)

## 5. Entity Update Pattern

### 5.1 New Creation

```
mapper.toEntity(domain) → jpaRepository.save(entity) → mapper.toDomain(entity)
```

### 5.2 Existing Update

```
mapper.toEntity(domain with id) → jpaRepository.save(entity) → mapper.toDomain(entity) (JPA merge)
```

- Update strategy is standardized to **merge via save()**
- **Do not use dirty checking update flow** (`findById` + mutate entity) in this project
- Repository implementation is responsible for Domain ↔ Entity conversion

## 6. Package Structure

```
{domain}/
├── application/
│   ├── service/       # Application services
│   ├── facade/        # Facade services
│   ├── port/out/      # Outbound ports
│   │   ├── client/    # Cross-BC access interfaces
│   │   │   └── {other-domain}/{OtherDomain}Port
│   │   ├── query/     # Use-case specific complex queries
│   │   │   ├── {Domain}QueryPort
│   │   │   └── criteria/  # Query criteria objects
│   │   └── util/      # Utility ports (PasswordEncoder, etc.)
│   └── dto/
│       ├── in/        # Input DTOs
│       └── out/       # Output DTOs
├── domain/
│   ├── model/         # Domain models + enum/ + vo/
│   ├── repository/    # Repository interfaces (CQRS)
│   │   └── vo/        # Pagination VOs (PageCriteria, PageResult)
│   ├── event/         # Domain events
│   └── service/       # Domain services
├── infrastructure/
│   ├── jpa/           # JPA repositories
│   ├── repository/    # Repository implementations
│   ├── acl/           # Cross-BC access implementations
│   │   └── {other-domain}/{OtherDomain}PortImpl
│   ├── query/         # QueryPort implementations
│   │   └── {Domain}QueryPortImpl
│   ├── querydsl/      # QueryDSL query implementations
│   │   └── {Domain}QuerydslRepository
│   └── entity/        # JPA entities
├── interfaces/
│   ├── web/               # REST API
│   │   ├── controller/    # REST controllers
│   │   ├── request/       # Request objects
│   │   └── response/      # Response objects
│   └── event/         # Event listeners
└── support/
    ├── common/        # Common utilities + error/
    └── config/        # Domain-specific configuration
```

## 7. Prohibited Actions

- Controller directly calling Repository
- Input normalization in Facade/Service (domain model responsibility)
- Using dirty checking (`findById` + mutate + flush) as the default update strategy
- RepositoryImpl에서 `DataIntegrityViolationException` catch하여 비즈니스 예외 변환 (Repository는 데이터 반환만 담당, 비즈니스 예외는 Service 책임)
- Writing business logic in Controller/Facade
- Using framework annotations on domain models
- Class-level `@Transactional` annotation (must be method-level)
- Service calling another Service (orchestration belongs in Facade)
- Port만 감싸는 thin wrapper Service 생성 금지 (Port 호출은 기존 Service에 통합)
- ACL directly calling provider Service/Repository/JPA/QueryDSL/Entity (must call provider **Facade** only)
