---
description: CQRS project-specific feature development workflow (Explore → Question → Validate → TDD Implementation → Review)
argument-hint: Feature description to implement (e.g., Product registration API)
---

# Feature Development (CQRS Customized)

A systematic workflow to help developers implement new features. Understand the codebase first, ask questions, validate the design against existing architecture patterns, then implement with TDD.

## Core Principles

- **Ask first**: Identify all ambiguities, edge cases, and unspecified behaviors. Do not assume — ask and wait for answers.
- **Understand before acting**: Read and understand existing code patterns first
- **Read files identified by agents**: After running agents, always read the key files they return to gain detailed context
- **Follow existing patterns**: This project has a confirmed CQRS architecture. Do not propose new architectures — create implementation blueprints that match existing patterns
- **TDD workflow**: Always follow the Red → Green → Refactor cycle
- **Use TodoWrite**: Track progress throughout all phases

## Project Architecture Summary (CQRS)

**Architecture Principle**: `Application → Domain ← Infrastructure` (Dependency Inversion)

```
Controller → Facade(@Transactional) → Service → Repository(interface, domain/repository/) → RepositoryImpl → JpaRepository + Entity ↔ Domain
```

**Layer Rules**:
- Facade calls only Service (direct calls to Repository, Port, DomainService prohibited)
- Service calls Repository, Port, DomainService
- DomainService must not call Repository/Port (Service passes data to it), stateless design
- Domain Model contains only pure business logic (no external dependencies)
- Repository Interface defined in `domain/repository/`, implementations in `infrastructure/repository/`

**BC (Bounded Context) Boundaries**:
- `catalog`: Brand, Product (same BC → direct Service call allowed)
- `engagement`: Like (cross-BC → access via Client/ACL)
- `ordering`: Order, OrderItem (cross-BC → access via Client/ACL)
- `user`: User (cross-BC → access via Client/ACL)

**Cross-BC Communication**:
- Sync: `application/client/{domain}/{Domain}Client` (interface) + `infrastructure/acl/{domain}/{Domain}ClientImpl` (implementation)
- Async: Domain events + `@TransactionalEventListener` (eventual consistency)

**Domain Model Patterns**: `create()` + `reconstruct()` factories, private constructors, VOs as Java records

---

## Phase 1: Discovery

**Goal**: Understand what needs to be built

Request: $ARGUMENTS

**Actions**:
1. Create a todo list for all phases
2. If the feature is unclear, ask the user:
   - What problem are you trying to solve?
   - How should the feature behave?
   - Are there any constraints or requirements?
3. Summarize your understanding and confirm with the user

---

## Phase 2: Codebase Exploration

**Goal**: Understand related existing code and patterns by CQRS layer

**Actions**:
1. Run 2-3 `code-explorer` agents in parallel. Each agent should:
   - Comprehensively trace code through the CQRS layers
   - Focus on different aspects (similar feature implementations, layer structure, domain model patterns, etc.)
   - Include a list of 5-10 key files that must be read

   **Example agent prompts**:
   - "Find features similar to [feature] and trace the flow: Controller → Facade → Service → Repository → Entity → Domain"
   - "Map the architecture and abstractions of [domain area]. Specifically trace the DTO conversion flow (Request → InDto → Domain → OutDto → Response)"
   - "Analyze the domain model (factory methods, VOs, validation logic) and test patterns of [existing feature]"

2. Read all files returned by agents to gain deep understanding
3. Present a comprehensive summary of discovered patterns and conventions

---

## Phase 3: Clarifying Questions

**Goal**: Resolve all ambiguities before design

**CRITICAL: Never skip this phase.**

**Actions**:
1. Review codebase analysis results and the original feature request
2. Identify unspecified items:
   - Edge cases and error handling (which ErrorType to use)
   - Domain model design (which fields are immutable, which to separate as VOs)
   - Validation rules (null → empty → length → format → business)
   - Integration points and Cross-BC communication needs
   - Performance requirements
3. **Present all questions as an organized list to the user**
4. **Proceed to the next phase only after receiving answers**

---

## Phase 4: Pattern Validation & Blueprint

**Goal**: Create and validate an implementation blueprint matching existing patterns

**NOTE**: This project has a confirmed CQRS architecture. The question is NOT "which architecture to use" but "how to implement within the confirmed architecture."

**Actions**:
1. Run 2-3 `code-architect` agents in parallel:
   - Agent 1: Analyze CQRS implementation patterns from similar existing domains to create an implementation blueprint
   - Agent 2: Design domain model blueprint (factory methods, VOs, validation logic, Entity mapping)
   - Agent 3: Test strategy blueprint (unit tests, integration tests, E2E test structure)
2. Review all blueprints and merge into a single unified implementation plan
3. Present to user: list of files to create/modify, implementation details per layer, domain model design, test strategy
4. **Start implementation only after user approval**

---

## Phase 5: TDD Implementation

**Goal**: Implement the feature with TDD (Red → Green → Refactor)

**Do not start implementation without explicit user approval.**

**Actions**:
1. Wait for explicit user approval
2. Read all related files identified in previous phases
3. **Implement with TDD cycles**:

   **Red Phase**: Write failing tests first
   - Write tests following the 3A principle (Arrange-Act-Assert)
   - `@DisplayName`: `[methodName()] condition -> result. Detailed description` format
   - Exception verification: `assertThrows` + `assertAll(errorType, message)` pattern

   **Green Phase**: Write minimal code to pass tests
   - No over-engineering
   - Strictly follow existing codebase conventions

   **Refactor Phase**: Remove unnecessary code and improve quality
   - Remove unused imports
   - All tests must pass

4. Update progress via todo

---

## Phase 6: Quality Review

**Goal**: Verify code quality, pattern compliance, and bugs

**Actions**:
1. Run 3 `code-reviewer` agents in parallel:
   - Agent 1 (Pattern compliance): Verify CQRS layer rules, domain model patterns, DTO flow
   - Agent 2 (Bugs/Consistency): Logic errors, null handling, exception handling, test coverage
   - Agent 3 (Conventions): DisplayName format, ErrorType checklist, Entity update patterns, test patterns
2. Consolidate results, identify most critical issues
3. **Present findings to user and ask how to proceed** (fix now / fix later / proceed as-is)
4. Handle issues based on user's decision

---

## Phase 7: Summary

**Goal**: Summarize completed work

**Actions**:
1. Mark all todos as complete
2. Summary:
   - Implemented features
   - Key decisions made
   - Modified/created files
   - Suggested next steps (additional tests, documentation, related APIs, etc.)

---
