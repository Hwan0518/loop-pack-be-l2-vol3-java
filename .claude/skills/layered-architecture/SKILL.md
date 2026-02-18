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
        → Domain Service (domain/service/)
        → Domain Model (domain/model/)
      → Repository Interface (domain/repository/)
        → RepositoryImpl (infrastructure/repository/)
          → JPA Repository (infrastructure/jpa/)
            → Entity (infrastructure/entity/) ↔ Domain Model
  → Controller
→ Client Response
```

## 2. Layer Responsibilities

| Layer | Class Pattern | Annotation | Role |
|-------|--------------|-----------|------|
| Controller | `{Domain}Controller` | `@RestController` | Receive requests, return responses, call Facade |
| Facade (Command) | `{Domain}CommandFacade` | `@Service`, `@Transactional` | Command use-case orchestration, transaction boundary |
| Facade (Query) | `{Domain}QueryFacade` | `@Service`, `@Transactional(readOnly=true)` | Query use-case orchestration |
| Service | `{Domain}CommandService` | `@Service`, `@Transactional` | Single domain business logic execution |
| Domain Service | `{Domain}XxxValidator`, etc. | Pure class (`@Bean` registration) | Stateless, mediates domain object collaboration within same BC, business invariant verification |
| Repository (I) | `{Domain}CommandRepository` | Interface | Command contract (save, delete) |
| Repository (I) | `{Domain}QueryRepository` | Interface | Query contract (find, exists) |
| RepositoryImpl | `{Domain}Command/QueryRepositoryImpl` | `@Repository` | Entity ↔ Domain conversion then JPA call |
| Entity | `{Domain}Entity` | `@Entity` | DB mapping, `from(Domain)` + `toDomain()` |
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
| `engagement` | Like | User engagement |
| `ordering` | Order, OrderItem | Orders |
| `user` | User | Users |

- **Same BC**: Facade can directly call another domain's Service within the same BC
- **Cross-BC (sync)**: Port interface (`application/port/out/client/`) + ACL implementation (`infrastructure/acl/`) pattern
- **Cross-BC (async)**: Domain events + `@TransactionalEventListener` (eventual consistency)

### 3.5 Domain Repository Rules

- **MUST**: Signatures use only domain language (`User`, `Long`, `Optional<User>`, `List<User>`, `PageCriteria`, `PageResult`)
- **MUST NOT**: No `Page`, `Pageable`, `Slice`, `Sort`, `Specification` or other Spring/JPA type exposure
- **MUST NOT**: No use-case DTO (OutDto, etc.) returns — only Domain Model
- Infrastructure implementations handle Spring type ↔ domain type conversion

### 3.6 Use-Case Specific Query (QueryPort)

Use QueryPort when complex queries, Projections, or direct DTO returns are needed.

- Interface: `{domain}/application/port/out/query/{Domain}QueryPort`
- Criteria: `{domain}/application/port/out/query/criteria/{Domain}SearchCriteria`
- Implementation: `{domain}/infrastructure/query/{Domain}QueryPortImpl`
- **"Repository" naming prohibited** — must use `QueryPort`
- Service calls through QueryPort interface

### 3.7 Port Naming Rules

| Type | Location | Naming |
|------|----------|--------|
| Cross-BC (client) | `application/port/out/client/` | Descriptive naming (`LikeTargetValidator`, `CartProductReader`, etc.) |
| Use-case query | `application/port/out/query/` | `...QueryPort` |
| Utility (util) | `application/port/out/util/` | Keep existing naming (`...Port` suffix not used) |

## 4. Data Conversion Flow (DTO Pattern)

```
Request → toInDto() → [Facade] → Domain → OutDto.from(domain) → [Controller] → Response.from(outDto)
```

### 4.1 DTO Type Rules

| DTO | Location | Conversion Method | Role |
|-----|----------|------------------|------|
| Request | `interfaces/controller/request/` | `toInDto()` | Receive external input, apply Jakarta Validation |
| InDto | `application/dto/in/` | None (immutable record) | Pass input between layers |
| OutDto | `application/dto/out/` | `from(Domain)` static factory | Domain → output conversion |
| Response | `interfaces/controller/response/` | `from(OutDto)` static factory | Final response (masking possible) |

### 4.2 DTO Rules

- All DTOs implemented as immutable records
- Apply `@NotBlank`, `@NotNull`, etc. Jakarta Validation to Request
- **Prohibited**: including sensitive info (password, etc.) in Response
- Data masking possible in Response (e.g., last character of name)

## 5. Entity Update Pattern

### 5.1 New Creation

```
Entity.from(domain) → jpaRepository.save(entity) → entity.toDomain()
```

### 5.2 Existing Update

```
jpaRepository.findById(id) → existingEntity.updateXxx(...) → JPA dirty checking → entity.toDomain()
```

- `Entity.from(domain)` always creates a **new entity (no id)**
- **Absolutely prohibited** to use `Entity.from(domain)` for updates — query existing entity and modify

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
│   └── entity/        # JPA entities
├── interfaces/
│   ├── controller/    # REST controllers + request/ + response/
│   └── event/         # Event listeners
└── support/
    ├── common/        # Common utilities + error/
    └── config/        # Domain-specific configuration
```

## 7. Prohibited Actions

- Controller directly calling Repository
- Input normalization in Facade/Service (domain model responsibility)
- Using `Entity.from()` for Entity updates
- Writing business logic in Controller/Facade
- Using framework annotations on domain models
