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

**Note:** For domain models without dependency fields, place Javadoc above the class declaration.

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

> Detailed inline comment examples (Sequential/Conditional): [references/detail.md](references/detail.md)

---

## Layer Summary

| Layer           | Class Comment          | Method Comment                | Dependency Injection                |
|-----------------|------------------------|-------------------------------|-------------------------------------|
| Domain Model    | Javadoc field listing  | Javadoc logic list + `// N.`  | -                                   |
| Service/Facade  | Javadoc method listing | `// N.` matching              | `// service`, `// repository`, etc. |
| Controller      | Javadoc API listing    | `// N.` matching              | `// service`, `// util`, etc.       |
| Repository Impl | None/brief             | `//` brief description        | `// jpa`, `// util`, etc.           |
| Event Handler   | Javadoc event desc     | `// N.` matching              | -                                   |
| Test            | `@DisplayName` Korean  | `// given/when/then`          | -                                   |
| Fixture         | `//` constant grouping | Javadoc factory description   | -                                   |

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
