---
name: comment-style
description: >
  Skill for writing and reviewing comments in Java source code.
  Applies project-specific comment conventions: business descriptions in Korean, structural markers in English, numbered method matching, layer-specific patterns.
---

# Comment Style Guide

Project comment conventions derived from actual codebase analysis. Ensures consistent comment style across all layers of the hexagonal architecture.

## When to Activate

- Writing new Java classes (domain models, services, controllers, etc.)
- Adding or modifying comments in existing code
- Checking comment style consistency during code review
- Writing test classes, fixtures, event handlers
- Organizing dependency injection fields

---

## Core Rules

### Comment-First Workflow

Comments are **small design** — sketch the logic flow as comments first, then fill in code underneath.

**Class-level workflow:**

1. Write class Javadoc (field listing or method listing)
2. Write `// N.` method marker comments
3. Write method body comments (logical steps)
4. Fill in code under each comment

**Method-level workflow:**

1. List logical steps as `//` comments inside the method body
2. Write code under each comment

```java
// Step 1: Sketch comments
// 1. 브랜드 생성
public Brand createBrand(BrandCreateInDto inDto) {

	// 브랜드명 중복 검증

	// 브랜드 생성

	// 브랜드 저장
}

// Step 2: Fill in code
// 1. 브랜드 생성
public Brand createBrand(BrandCreateInDto inDto) {

	// 브랜드명 중복 검증
	brandQueryRepository.existsByName(inDto.name());

	// 브랜드 생성
	Brand brand = Brand.create(inDto.name());

	// 브랜드 저장
	return brandCommandRepository.save(brand);
}
```

**Key principle:** Comments lead, code follows. Never write code first and add comments after.

---

### 1. Language Rules

- **Business logic/descriptions**: always Korean / **Structural markers**: always lowercase English

```java
// ❌ WRONG                          // ✅ CORRECT
/** Trade management service */      /** 거래 관리 서비스 */
// 서비스                             // service
```

### 2. Comment Style Selection

| Style            | Use For                                |
|------------------|----------------------------------------|
| `/** */` Javadoc | Class documentation, complex method/field descriptions, test fixture factories |
| `//` Single-line | Inline descriptions, structural markers, numbered matching, given/when/then |
| `/* */` Block    | **Do not use**                         |

### 3. No Over-Commenting

No comments on self-explanatory code: simple getters/setters, methods whose names are descriptive, boilerplate, fields whose names explain their purpose.

### 4. Class Javadoc Placement

Class Javadoc is placed **inside the class body**, after dependency fields and before the first method.

```java

@Service
@RequiredArgsConstructor
public class TradeManagementService {

	// repository
	private final TradeCommandRepository tradeCommandRepository;


	/**
	 * 거래 관리 서비스
	 * 1. 거래 생성
	 */

	// 1. 거래 생성
	public void createTrade(...) { ...}

}
```

**Note:** For domain models without dependency fields, place field-listing Javadoc inside the class body, before fields. For infrastructure classes (Entity, Mapper), place Javadoc above the class declaration.

### 5. Numbered Method Matching

Class Javadoc numbered list → each method matched with `// N.` comment:

```java
/**
 * 유저 관리 서비스
 * 1. 유저 생성
 * 2. 유저 수정
 */

// 1. 유저 생성
public void createUser(...) { ...}


// 2. 유저 수정
public void updateUser(...) { ...}
```

### 6. Domain Model Field Listing

List fields in Javadoc with `- fieldName: Korean description` format. If self-explanatory, `- 거래번호` format is also acceptable.

### 7. Inline Comments for Multi-Line Methods

- **1-line methods**: `// N.` comment alone is sufficient
- **2+ line methods**: mandatory `// Korean description` inline comments for each logical step

**Blank line formatting (mandatory):**

- `{` 뒤 빈 줄 1개 (메서드 본문 시작)
- 각 논리 단계(`// 주석` + 코드 블록) 사이 빈 줄 1개

```java
// ✅ CORRECT
public void createTrade(CreateTradeInDto inDto) {
                                                    // ← blank line after {
	// 탄소 감소량 계산
	BigDecimal carbonReduction = calculate(inDto);
                                                    // ← blank line between steps
	// 거래 생성
	Trades trade = Trades.from(command);
}
```

> Detailed inline comment examples (Sequential/Conditional): [references/detail.md](references/detail.md)

---

## Layer Summary

| Layer           | Class Comment          | Javadoc Placement            | Method Comment                | Dependency Injection                |
|-----------------|------------------------|------------------------------|-------------------------------|-------------------------------------|
| Domain Model    | Javadoc field listing  | Inside class body, before fields | Javadoc logic list + `// N.`  | -                                   |
| Service/Facade  | Javadoc method listing | Inside class body, after deps | `// N.` matching              | `// service`, `// repository`, etc. |
| Controller      | Javadoc API listing    | Inside class body, after deps | `// N.` matching              | `// service`, `// util`, etc.       |
| Repository Impl | None/brief             | -                            | `//` brief description        | `// jpa`, `// util`, etc.           |
| Entity          | Javadoc field listing  | Above class declaration      | -                             | -                                   |
| Mapper          | Javadoc method listing | Above class declaration      | `// N.` matching              | -                                   |
| Event Handler   | Javadoc event desc     | Inside class body            | `// N.` matching              | -                                   |
| Test            | `@DisplayName` Korean  | -                            | `// given/when/then`          | -                                   |
| Fixture         | `//` constant grouping | -                            | Javadoc factory description   | -                                   |

> Full code examples per layer: [references/detail.md](references/detail.md)

---

## Structural Markers

| Marker          | Purpose      | Marker                                   | Purpose       |
|-----------------|-------------|------------------------------------------|---------------|
| `// service`    | Service class| `// event`                               | Event publisher|
| `// repository` | Repository   | `// jpa`                                 | JPA repository |
| `// port`       | Port interface| `// facade`                             | Facade        |
| `// util`       | Utility      | `// field` / `// converter` / `// value` | Others        |

---

## Output Contract & Expectations

**MUST — when writing new files:**

1. **Class Javadoc** — field listing (domain) or method listing (service/controller)
2. **Structural markers** — dependency injection field grouping (when applicable)
3. **`// N.` numbered comments** — matching class Javadoc list
4. **Korean** — all business descriptions / **English** — all structural markers
5. Tests: `// given` / `// when` / `// then` + `@DisplayName` in Korean
6. Inline comments for each logical step in 2+ line methods

**Before writing code:** Check architecture layer + adjacent file comment style
**When modifying code:** Adding/removing methods → sync Javadoc + numbered comments, preserve existing style

**NEVER:**

- Write business comments in English
- Use `/* */` block comments
- Add comments to self-explanatory code
- Leave numbered comments and Javadoc list out of sync

---

## Summary Checklist

- [ ] **Language**: Business=Korean, Markers=English
- [ ] **Class-Level**: Javadoc exists + placed inside class body + domain field list or service method list
- [ ] **Method-Level**: `// N.` numbered matching + inline comments for 2+ lines
- [ ] **Dependencies**: Structural marker grouping (lowercase English)
- [ ] **Tests**: `@DisplayName` Korean + `// given/when/then` + fixture Javadoc

> Anti-patterns and detailed examples: [references/detail.md](references/detail.md)
