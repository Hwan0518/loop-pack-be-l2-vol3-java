---
name: create-endpoint
description: TDD-based REST endpoint creation workflow. Use when implementing a new API endpoint from scratch.
---

# Create Endpoint

## 1. Implementation Order (Inside-Out TDD)

Start from the domain model and expand outward to outer layers.

```
1. Domain Model  →  2. Repository  →  3. Service  →  4. Facade  →  5. Controller  →  6. E2E Test
```

Repeat the **Red → Green → Refactor** cycle at each step.

## 2. Step-by-Step Checklist

### 2.1 Domain Model

- [ ] Create domain model class
- [ ] `create()` factory method (with validation, id = null)
- [ ] `reconstruct()` factory method (skip validation, with id)
- [ ] Constructor restricted to `private`
- [ ] Validation order: null → empty → length → format → business
- [ ] Input normalization (trim, toLowerCase, etc.) — only in `create()`
- [ ] Separate Value Objects if needed
- [ ] **Tests**: normal creation, failure cases for each validation rule

### 2.2 Repository

- [ ] Command Repository interface (save, delete) — location: `domain/repository/`
- [ ] Query Repository interface (find, exists) — location: `domain/repository/`
- [ ] Entity class (`of(...)` 팩토리 메서드)
- [ ] EntityMapper class (`@Component`, `toEntity(Domain)` + `toDomain(Entity)`)
- [ ] JPA Repository interface
- [ ] CommandRepositoryImpl — location: `infrastructure/repository/`
- [ ] QueryRepositoryImpl — location: `infrastructure/repository/`
- [ ] **Tests**: save then query, existence check, domain conversion accuracy

### 2.3 Service

- [ ] CommandService class (`@Service`, `@Transactional`)
- [ ] Single domain business logic implementation
- [ ] Keep public methods as use-case contracts; make internal step helpers `private`
- [ ] Create Domain Service if needed (pure Java, `@Bean` registration)
- [ ] **Tests**: Mock-based unit tests, normal/exception cases

### 2.4 Facade

- [ ] CommandFacade (`@Service`, `@Transactional`)
- [ ] QueryFacade (`@Service`, `@Transactional(readOnly = true)`)
- [ ] Use-case orchestration (composing Service calls)
- [ ] Authentication/authorization validation (if needed)
- [ ] **Tests**: Mock-based unit tests, including authentication failure cases

### 2.5 Controller

- [ ] `@RestController` class
- [ ] Request DTO (with Jakarta Validation)
- [ ] Response DTO (`from(OutDto)` factory, exclude sensitive info)
- [ ] InDto / OutDto
- [ ] Endpoint method (`@PostMapping`, etc.)
- [ ] **Tests**: MockMvc-based, normal response + Validation failure + error response

### 2.6 E2E Test

- [ ] `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`
- [ ] TestContainers Config Import
- [ ] `@AfterEach` DB cleanup (`DatabaseCleanUp.truncateAllTables()`)
- [ ] API call helper methods (test data creation)
- [ ] `@Nested` classes for endpoint grouping
- [ ] Normal scenario + all error scenario verification

## 3. Data Flow Verification

```
Request
  → toInDto()
    → [Facade] authentication/authorization validation
      → [Service] business logic
        → [Domain] domain validation + creation
          → [Repository] mapper.toEntity(domain) → save → mapper.toDomain(entity)
      → OutDto.from(domain)
    → [Controller] Response.from(outDto)
→ HTTP Response
```

## 4. Adding Error Types (If Needed)

When new domain business errors are needed:
1. Add new value to `ErrorType` enum
2. Update `ErrorTypeTest` (provider + count)
3. Use `throw new CoreException(ErrorType.XXX)` in domain code
4. Verify error response in E2E test

## 5. When Cross-BC Data Is Needed (Referencing Another Bounded Context)

Add these steps when data from another BC is required:

- [ ] Create Port interface — location: `{domain}/application/port/out/client/{other-domain}/{OtherDomain}Port`
- [ ] Create ACL implementation — location: `{domain}/infrastructure/acl/{other-domain}/{OtherDomain}PortImpl`
- [ ] ACL implementation calls **provider Facade (or facade-style dedicated API)** only
- [ ] ACL implementation stays thin: mapping/delegation only (no business/orchestration/error-mapping logic)
- [ ] Error mapping is done in the Service that calls the ACL
- [ ] Never call provider `Service/Repository/JPA/QueryDSL/Entity` directly from ACL (must call provider **Facade** only)
- [ ] Service calls through the Port interface
- [ ] **Tests**: Port Mock-based unit tests

> BC boundaries: catalog(Brand, Product) / engagement(ProductLike, BrandLike) / cart(CartItem) / ordering(Order, OrderItem) / user(User)

## 5.5 When QueryPort Is Needed (Use-Case Specific Complex Queries)

When complex queries, Projections, or direct DTO returns are needed:

- [ ] Create QueryPort interface — location: `{domain}/application/port/out/query/{Domain}QueryPort`
- [ ] Create criteria object (if needed) — location: `application/port/out/query/criteria/{Domain}SearchCriteria`
- [ ] Create QuerydslRepository — location: `{domain}/infrastructure/querydsl/{Domain}QuerydslRepository` (QueryDSL query logic)
- [ ] Create QueryPortImpl implementation — location: `{domain}/infrastructure/query/{Domain}QueryPortImpl` (thin adapter, delegates to QuerydslRepository)
- [ ] "Repository" naming prohibited (except QuerydslRepository)
- [ ] Service calls through QueryPort interface
- [ ] **Tests**: QueryPort Mock-based unit tests, QueryPortImpl integration tests

## 6. Completion Criteria

- [ ] All layer unit tests passing
- [ ] E2E tests passing (normal + error scenarios)
- [ ] Code coverage target met
- [ ] Unnecessary imports and debug code removed
- [ ] 1:1 mapping verified against REQUIREMENTS.md feature requirements
- [ ] Lint/format checks passing

## 7. Cautions

- No layer skipping (Controller → Facade → Service → Repository)
- Business logic only in Domain Model/Domain Service
- Entity 업데이트 시에도 `mapper.toEntity(domain)` → `save()` → `mapper.toDomain(entity)` 패턴 사용 (id 포함 시 JPA merge)
- Never include sensitive info (password, etc.) in Response
- Input normalization only in Domain Model's `create()` factory
