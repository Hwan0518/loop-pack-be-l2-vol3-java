# Core Rules Integration — Document/Skill/Plugin Update Plan

## Context

Checklist-based core rules have been added. The following 6 items were finalized through discussion with the developer:

1. **Repository Interface → ALL Domain Layer** (`domain/repository/`)
2. **Test Doubles → All Types** (Fake/Stub/Mock/Spy) — choose the appropriate one for the situation
3. **DomainService → Keep Current Behavior** (no Repository calls; Service passes data)
4. **Package Structure → Keep Current** + only move Repository Interface to `domain/repository/`
5. **Bounded Context (BC) Boundaries Introduced** (documentation only; code restructuring on separate instruction)
   - `catalog` BC: Brand, Product (same BC → direct Service calls allowed)
   - `engagement` BC: Like (different BC → access via Client/ACL)
   - `ordering` BC: Order, OrderItem (different BC → access via Client/ACL)
   - `user` BC: User (different BC → access via Client/ACL)
6. **Cross-BC Communication Patterns**
   - Interface: `application/port/out/client/{domain}/{Domain}Port.java`
   - Implementation: `infrastructure/acl/{domain}/{Domain}PortImpl.java`
   - Synchronous (exception required): Port + ACL (e.g., stock deduction, point deduction)
   - Asynchronous (eventual consistency): Event (`@TransactionalEventListener`)

Additional items:
- Architecture principle: `Application → Domain ← Infrastructure` (dependency inversion)
- Domain Service: stateless design, coordinates domain object collaboration within the same BC
- **Domain Repository purity**: Signatures use only domain language; Spring/JPA type exposure prohibited
- **Use-case specific QueryPort pattern**: Complex queries separated into Application Layer ports
- **Unified Port package structure**: `client/`, `query/`, `util/` under `application/port/out/`
- **Pagination VO**: PageCriteria, PageResult defined in `domain/repository/vo/`

---

## Target Files and Changes

### 1. CLAUDE.md (`/Users/dhwan/Dev/loop-pack-be-l2-vol3-java/CLAUDE.md`)

#### 1-1. Section 3 Package Structure (line 51~85)
- `application/client/` → `application/port/out/client/` (moved under port)
- `application/port/out/query/` newly added
- `application/port/out/util/` newly added
- `domain/repository/vo/` newly added (PageCriteria, PageResult)
- `infrastructure/query/` newly added
- `infrastructure/acl/` implementation naming: `...ClientImpl` → `...PortImpl`

**After (full package structure):**
```
{domain}/
├── application/
│   ├── service/
│   ├── facade/
│   ├── port/
│   │   └── out/
│   │       ├── client/                        # Cross-BC ports
│   │       │   └── {other-domain}/
│   │       │       └── {OtherDomain}Port
│   │       ├── query/                         # Use-case specific query ports
│   │       │   ├── {Domain}QueryPort
│   │       │   └── criteria/
│   │       │       └── {Domain}SearchCriteria
│   │       └── util/                          # Utility ports (existing naming preserved)
│   │           ├── PasswordEncoder
│   │           └── AuthenticationManager
│   └── dto/
│       ├── in/
│       └── out/
├── domain/
│   ├── model/ + enum/ + vo/
│   ├── repository/                            # Domain repository interfaces (CQRS)
│   │   ├── {Domain}CommandRepository
│   │   ├── {Domain}QueryRepository
│   │   └── vo/                                # Repository contract VOs
│   │       ├── PageCriteria
│   │       └── PageResult<T>
│   ├── event/
│   └── service/
├── infrastructure/
│   ├── jpa/
│   ├── repository/                            # Domain repository implementations
│   ├── acl/                                   # Cross-BC port implementations
│   │   └── {other-domain}/
│   │       └── {OtherDomain}PortImpl
│   ├── query/                                 # QueryPort implementations
│   │   └── {Domain}QueryPortImpl
│   └── entity/
├── interfaces/
│   ├── controller/ + request/ + response/
│   └── event/
└── support/
    ├── common/ + error/
    └── config/
```

#### 1-2. Section 4.3 Unit Test Mock Patterns (line 151~155)
- Rename title: "Unit Test Mock Patterns" → "Unit Test Patterns"
- Add first line: "All test doubles (Fake, Stub, Mock, Spy) are allowed — choose the appropriate one for the situation"
- Keep existing Mockito description with "When using Mocks:" prefix

#### 1-3. Section 4.6 Domain Service (line 226~230)
- Add below existing rules:
  - `- **Stateless** design: coordinates domain object collaboration within the same Bounded Context (BC)`

#### 1-4. Section 4.7 CQRS Layer Flow (line 232~)
- Add architecture principle at the beginning of the section:
```
#### Architecture Principle
- Dependency direction: `Application → Domain ← Infrastructure`
- Domain Layer is the center; Application and Infrastructure depend on Domain
- Repository Interface is defined in Domain Layer; implementation resides in Infrastructure
```
- Layer table: Update Repository (I) location to reflect `domain/repository/`
- **Add QueryPort rows to layer table:**

| Layer | Class | Location | Role |
|-------|-------|----------|------|
| QueryPort(I) | `{Domain}QueryPort` | `application/port/out/query/` | Use-case specific complex query contract |
| QueryPortImpl | `{Domain}QueryPortImpl` | `infrastructure/query/` | QueryPort implementation (JPA/QueryDSL) |

#### 1-5. Section 4.7 Cross-BC Communication (line 250~253)
- Significantly expand existing Cross-BC rules:
- **Naming update**: `Client` → `Port`, `ClientImpl` → `PortImpl`
- **Location update**: `application/client/` → `application/port/out/client/`

```
#### Bounded Context Boundaries

| BC | Included Domains | Description |
|----|-----------------|-------------|
| `catalog` | Brand, Product | Product catalog |
| `engagement` | Like | User engagement |
| `ordering` | Order, OrderItem | Orders |
| `user` | User | Users |

##### Intra-BC Communication
- Facade can directly call Services of other domains within the same BC
- Example: `ProductQueryFacade` calls `BrandQueryService` (within catalog BC)

##### Inter-BC Communication (Synchronous)
- Port interface + ACL implementation pattern
- Interface: `{domain}/application/port/out/client/{other-domain}/{OtherDomain}Port`
- Implementation: `{domain}/infrastructure/acl/{other-domain}/{OtherDomain}PortImpl`
- Only the implementation may directly reference other domain's domain model and JPA
- Example: Stock deduction on order → `ProductStockPort` + `ProductStockPortImpl`

##### Inter-BC Communication (Asynchronous)
- Domain events + `@TransactionalEventListener`
- Used for side effects requiring only eventual consistency
- Example: Send notification after order completion, update statistics
```

#### 1-6. New Section: Query Approach Decision Guide (added to Section 4.7)

```
#### Query Approach Decision Guide

##### Repository (Domain Layer)
- **Ownership**: Storage contract owned by Domain Layer
- **Return type**: Only Domain Model or its collections
- **Purpose**: Reconstruct/persist Domain Model state to execute core business rules
- **Decision question**: "Do I need the Domain Model object itself to execute domain logic (behavior)?"

✅ Recommended: Order cancellation request → `OrderRepository.findById(orderId)` to retrieve Order Domain Model
  → Execute `order.cancel()` business logic → Save
  (Core business rules reside inside Domain Model, isolated from persistence details)

❌ Anti-pattern: `OrderRepository.findAllSummary()` → returns `OrderSummaryDTO` for UI display
  (Domain Layer depends on UI requirements → dependency rule violation)

##### QueryPort (Application Layer)
- **Ownership**: Use-case specific query contract owned by Application Layer
- **Return type**: DTO or framework-independent read-only model
- **Purpose**: Optimized data retrieval for display or query performance (Join, Projection) without business logic
- **Decision question**: "Do I need data optimized for a specific use case/UI without business logic?"

✅ Recommended: My page order history → `MyOrderQueryPort.findMyOrders(userId)`
  → Infrastructure implementation performs optimized DB query → returns `OrderHistoryDTO` directly
  (No Domain Model behavior needed; direct DTO query is better for architecture and performance)

❌ Anti-pattern: Retrieve entire heavy Domain Model via UserRepository → extract only name/email for Controller
  (Domain Model directly exposed externally, increased coupling from security/maintainability perspective)

##### Decision Tree
1. Do you need to execute Domain Model methods (behavior) after the query?
   → YES: Use **Repository** (returns Domain Model, located in Domain Layer)
2. Do you simply need to process data for display or is query performance optimization (Projection) important?
   → YES: Use **QueryPort** (returns DTO, located in Application Layer)
```

#### 1-7. New Section: Domain Repository Purity Rules (added to Section 4.7)

```
#### Domain Repository Rules

##### MUST
- Signatures (methods/inputs/returns) use only domain language (Id, VO, Domain Model, PageCriteria, PageResult)
- Defined in Domain Layer (`domain/repository/`)

##### MUST NOT
- No Page, Pageable, JPA/QueryDSL type exposure
- No use-case response DTO (OutDto, etc.) returns
- No Spring framework type exposure
```

#### 1-8. New Section: Use-Case Specific Query (QueryPort) Pattern

```
#### Use-Case Specific Query (QueryPort)

- Complex queries, projections, and response DTO returns are separated into QueryPort
- Interface: `application/port/out/query/{Domain}QueryPort`
- Implementation: `infrastructure/query/{Domain}QueryPortImpl`
- "Repository" naming absolutely prohibited
- Can return use-case DTOs, PageResult<T>
- Query criteria: Use framework-independent criteria objects (no direct API DTO usage)
- Criteria location: `application/port/out/query/criteria/`
- Criteria naming: `{Domain}SearchCriteria` (e.g., OrderSearchCriteria)
```

#### 1-9. New Section: Port Naming Rules

```
#### Port Naming Rules

##### client/, query/ ports
- Suffix: unified `...Port` (e.g., ProductStockPort, OrderQueryPort)
- Vendor/framework names prohibited (Spring, BCrypt, etc. used only in implementations)
- Named to reveal role

##### util/ ports
- Do NOT use `...Port` suffix
- Keep existing naming (e.g., PasswordEncoder, AuthenticationManager)
- Interface: `application/port/out/util/`
- Implementation: `{domain}/support/common/util/` or `global/common/{core-function}/`
- **Classification criteria**: Only non-business technical utilities allowed (encryption, auth verification, etc.)
- External BC collaboration purpose → always `client/`, use-case query purpose → always `query/`
```

#### 1-10. New Section: Pagination VO (added to Domain Model Patterns or CQRS section)

```
#### Pagination VO (`domain/repository/vo/`)

##### Rationale
- Repository interfaces are defined in the Domain Layer, and the domain owns the data access contract
- Pagination parameters (page, size, sort) are part of this contract; the domain must be able to define data access methods without depending on infrastructure implementation details (Spring Page/Pageable)
- Therefore, framework-independent VOs (PageCriteria, PageResult) are placed in the domain layer so the domain expresses contracts in its own language

##### Definition
- PageCriteria: framework-independent record (page, size, sort)
- PageResult<T>: framework-independent record (content, page, size, totalElements)
- Location: `domain/repository/vo/`
- Usable by both Domain Repository and QueryPort
- Infrastructure implementations handle Spring Page/Pageable ↔ PageCriteria/PageResult conversion
```

---

### 2. layered-architecture/SKILL.md

#### 2-1. Layer Flow Diagram (line 10~23)
- `Repository Interface (application/repository/)` → `Repository Interface (domain/repository/)`

#### 2-2. Section 3 Core Rules (line 39~)
- Add architecture principle: `Application → Domain ← Infrastructure`
- Add BC rules: direct call within same BC, different BC → Port/ACL
- **Add Port structure**: explain `application/port/out/` sub-organization
- **Add QueryPort pattern**: explain use-case specific query contracts
- **Add Domain Repository purity rules**

#### 2-3. Section 6 Package Structure (line 104~127)
- Under `application/`: remove `client/`, add `port/out/client/`, `port/out/query/`, `port/out/util/`
- Under `domain/`: add `repository/vo/`
- Under `infrastructure/`: update `acl/` naming (`PortImpl`), add `query/`

---

### 3. domain-model/SKILL.md

#### 3-1. Section 6 Domain Service (line 78~101)
- Add: "**Stateless** design: coordinates domain object collaboration within the same BC"
- Add: "Complex use cases (cross-BC composition) are handled at the Application Layer (Facade) via Port"

#### 3-2. Pagination VO Addition
- Add PageCriteria, PageResult description under `domain/repository/vo/`
- Clarify their role as contract VOs in the domain layer alongside domain models

---

### 4. create-endpoint/SKILL.md

#### 4-1. Section 2.2 Repository (line 31~39)
- Specify Repository Interface location as `domain/repository/`
- Add comment: `# Location: domain/repository/`

#### 4-2. Add Cross-BC Checklist
- Add steps for creating Port interface + ACL implementation when data from another BC is needed

#### 4-3. Add QueryPort Creation Steps
- Add steps for creating QueryPort interface + implementation when complex queries/Projections are needed
- Reflect Port naming rules (`...Port` suffix)

---

### 5. tdd-workflow/SKILL.md

#### 5-1. Section 4.1 Unit Tests (line 122~127)
- "Use Mock framework" → "All test doubles (Fake, Stub, Mock, Spy) are allowed"
- Keep existing Mockito description with "When using Mocks:" prefix

#### 5-2. QueryPort Test Pattern Addition
- QueryPort unit tests: Stub QueryPort interface with Mock
- QueryPortImpl integration tests: TestContainers + actual DB query verification

---

### 6. feature-dev Plugin Files

#### 6-1. feature-dev.md (commands)
- Architecture summary:
  - Add `Application → Domain ← Infrastructure`
  - Change Repository location to `domain/repository/`
  - Add BC boundaries + Cross-BC rules (Port/ACL, Event)
  - **Reflect Port structure, QueryPort pattern, Repository purity rules**

#### 6-2. code-explorer.md (agents)
- Package Structure: `application/client/` → `application/port/out/client/`
- Add `application/port/out/query/`, `infrastructure/query/`
- Test patterns: reflect all test doubles
- Add BC boundary awareness

#### 6-3. code-architect.md (agents)
- Layer Rules: validate Repository Interface → `domain/repository/`
- Add BC Rules: validate Port/ACL usage between BCs, direct calls within same BC
- **Add Port Rules**: validate Port naming, QueryPort separation criteria
- **Repository purity validation**: confirm Domain Repository signatures have no Spring/JPA types
- Test Rules: allow all test doubles

#### 6-4. code-reviewer.md (agents)
- CQRS Layer Violations: validate Repository Interface location
- Add BC Violations: validate cross-BC direct references (calling another BC's Service without Port/ACL is prohibited)
- **Add Port Violations**: "Repository" naming prohibited in QueryPort, Spring type exposure prohibited in Domain Repository
- Test Violations: allow all test doubles

---

## Files NOT Changed

| File | Reason |
|------|--------|
| error-handling/SKILL.md | Unrelated to core rule changes |
| commit-protocol/SKILL.md | No commit convention changes |
| comment-style/SKILL.md | No comment convention changes |
| git-worktree/SKILL.md | No worktree management changes |
| requirements-analysis/SKILL.md | No requirements analysis process changes |
| plugin.json | No metadata changes needed |

---

## Verification

1. Grep to confirm no `application/repository` strings remain in target files
2. Verify `domain/repository` is consistently reflected in all related files
3. Verify test double terminology consistently changed from "Mock only" to "all test doubles"
4. Verify BC boundary definitions (catalog/engagement/ordering/user) are reflected in CLAUDE.md, layered-architecture, code-architect, code-reviewer
5. Verify Cross-BC rules (Port/ACL pattern) are reflected in CLAUDE.md, layered-architecture, create-endpoint, and feature-dev related files
6. Verify `application/client/` paths are consistently changed to `application/port/out/client/`
7. Verify Port naming is unified with `...Port` suffix (except util/)
8. Verify Domain Repository signatures have no Spring/JPA types
9. Verify "Repository" naming is not used in QueryPort
10. Verify PageCriteria/PageResult are located in `domain/repository/vo/`
