---
name: tdd-workflow
description: TDD (Red-Green-Refactor) based development workflow and test writing patterns. Use when tests need to be written for new feature implementation, bug fixes, or refactoring.
---

# TDD Workflow

## 1. TDD Cycle

### 1.1 Red Phase — Write Failing Tests

- [ ] Convert requirements to test cases
- [ ] Verify tests **compile but fail**
- [ ] Test only one behavior at a time (single responsibility)
- [ ] Separate boundary values and exception cases into individual tests

### 1.2 Green Phase — Pass with Minimal Code

- [ ] Write minimal code to make **all** Red Phase tests pass
- [ ] No over-engineering — do not write features not required by tests
- [ ] Verify existing tests are not broken

### 1.3 Refactor Phase — Improve Quality

- [ ] Remove unnecessary code, eliminate duplication
- [ ] Improve structure following OOP principles
- [ ] Remove unused imports
- [ ] Performance optimization (if needed)
- [ ] **All test cases must pass**

## 2. Test Writing Principles

### 2.1 3A Pattern (Arrange-Act-Assert)

```
// Arrange — Prepare test data and environment
<Create test target objects, set up Mocks, prepare input values>

// Act — Execute test target
<Single method call or single action>

// Assert — Verify results
<Compare expected vs actual values, verify exceptions, verify calls>
```

### 2.2 @DisplayName Writing Rules

**Format**: `[methodName()] condition -> result. Detailed description`

Requirements:
- Write **detailed enough** that requirements can be understood from the test alone
- Wrap method name in brackets, connect condition and result with arrow

Examples:
- `[POST /api/v1/users] 유효한 요청 -> 201 Created. 응답에 id, name 포함`
- `[save()] 유효한 엔티티 저장 -> ID가 할당된 엔티티 반환`
- `[create()] 8자 미만 비밀번호 -> INVALID_FORMAT 예외`

### 2.3 Coverage Target

- Aim for coverage **as close to 100% as possible**
- Verify all public methods, branches, and exception cases with tests
- High coverage is insufficient if **meaningful assertions** are missing

### 2.4 Test Case Derivation Techniques

Apply the following techniques to systematically derive test cases from requirements.

#### Boundary Value Analysis (BVA)

Inputs with ranges are prone to defects at **boundaries**. Test the boundary and just beyond it.

| Test Point | Example: 4-20 character field |
|------------|-------------------------------|
| Lower - 1 | 3 chars → fail |
| Lower (boundary) | 4 chars → pass |
| Upper (boundary) | 20 chars → pass |
| Upper + 1 | 21 chars → fail |

#### Equivalence Class Partitioning (ECP)

Divide inputs into groups (equivalence classes) that behave identically, then test only 1 representative value from each group.

| Class | Example: loginId field | Expected Result |
|-------|------------------------|-----------------|
| Valid value | `"validuser"` | Success |
| null | `null` | Error |
| Empty string | `""` | Error |
| Below min length | `"abc"` | Error |
| Above max length | `"a".repeat(21)` | Error |
| Disallowed characters | `"user@name"` | Error |

#### Other Techniques (Apply When Applicable)

- **Decision table**: When 2+ conditions combine, use a condition combination table to prevent omissions (e.g., header presence × user existence × password match)
- **State transition**: For domains with state machines, test each transition path and invalid transitions

#### Error Guessing

Supplement with tests for empirically common error patterns:
- null, empty string, whitespace only (`" "`, `"\t"`)
- Special characters, non-ASCII input (Korean, etc.)
- Max length + 1, duplicate values (unique constraint violations)
- Invalid formats (email without @, future dates)

## 3. Exception Verification Pattern

```
// Act — Action that triggers the exception
<ExceptionType> exception = assertThrows(<ExceptionType>.class,
    () -> targetMethod(args));

// Assert — Verify exception details
assertAll(
    () -> assertThat(exception.getErrorType()).isEqualTo(<errorType>),
    () -> assertThat(exception.getMessage()).isEqualTo(<errorMessage>)
);
```

## 4. Test Type Patterns

### 4.1 Unit Tests

- All test doubles (Fake, Stub, Mock, Spy) allowed — choose the appropriate one for the situation
- When using Mocks: `@ExtendWith(MockitoExtension.class)` + `@Mock` + manual constructor injection
- BDD style: `given().willReturn()`, `willThrow()`
- Verification: `verify()`, `never()`
- Parameterized tests: `@ParameterizedTest` + `@NullAndEmptySource` + `@ValueSource`
- QueryPort tests: Mock/Stub QueryPort interface for Service unit tests

### 4.2 Integration Tests

- Annotations: `@SpringBootTest`, `@ActiveProfiles("test")`
- Integrate with real infrastructure via TestContainers
- Import Config classes if needed
- QueryPortImpl integration tests: TestContainers + actual DB queries to verify QueryPort implementation

### 4.3 E2E Tests

- Location: `{domain}/interfaces/{Domain}ControllerE2ETest` (not under controller/)
- Annotations: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`
- Test isolation: DB cleanup in `@AfterEach` (do not use `@Transactional`)
- Test data: create directly via API call helper methods
- Structure: group by endpoint using `@Nested` classes

## 5. Error Type Addition Test Checklist

- [ ] Add new value to error type enum
- [ ] Add case to error type test's provider method
- [ ] Update `hasSize(N)` to N+1 in enum count verification test

## 6. Prohibited Actions

- Deleting failing tests to make them pass
- Writing production code without tests (follow TDD cycle)
- Tests that only work with Mocks but fail in real environments
- Disabling tests with `@Disabled` (requires approval even if temporary)
