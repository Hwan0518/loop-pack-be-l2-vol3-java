---
name: domain-model
description: Domain model design patterns (factory methods, Value Objects, domain services). Use when creating new domain models or adding business rules to existing models.
---

# Domain Model

## 1. Factory Method Pattern

| Method | Purpose | Validation | id |
|--------|---------|-----------|-----|
| `create(...)` | New object creation | Validation + normalization | null |
| `reconstruct(...)` | Restore from DB | Skip validation/normalization | DB value |

- Constructor must be restricted to `private`
- Only `create()` and `reconstruct()` can create instances

```
public class {Domain} {
    private {Domain}(Long id, String name) { ... }

    public static {Domain} create(String name) {
        // Validation → Normalization
        return new {Domain}(null, normalized);
    }
    public static {Domain} reconstruct(Long id, String name) {
        return new {Domain}(id, name);
    }
}
```

## 2. Validation Order

```
null check → empty check → length limit → format (regex) → business rules
```

- Each stage failure throws an exception with the corresponding `ErrorType`
- Must pass previous stage before proceeding to next

## 3. Input Normalization

- Performed **only** in the `create()` factory method
- **Prohibited** to duplicate normalization in Facade/Service (domain model has single responsibility)

| Field Type | Normalization | Example |
|------------|--------------|---------|
| Identifier (loginId, etc.) | `trim().toLowerCase()` | `"  Admin "` → `"admin"` |
| Name, email | `trim()` | `"  홍길동 "` → `"홍길동"` |
| Password | No normalization | Whitespace is meaningful |

## 4. Value Object (VO)

- Implemented as immutable records, performs its own validation, encapsulates business logic

| Method | Purpose | Validation |
|--------|---------|-----------|
| `create(rawValue)` | Create new value | Includes validation + conversion |
| `fromEncoded(encodedValue)` | Restore stored value | Skip validation |

```
public record Password(String value) {
    public static Password create(String raw) { /* validation → encoding */ }
    public static Password fromEncoded(String encoded) { return new Password(encoded); }
}
```

## 5. Field Mutability

| Type | Declaration | Modification | Examples |
|------|------------|-------------|---------|
| Immutable | `private final` | Cannot change, no setter | loginId, name, email |
| Mutable | `private` (non-final) | `changeXxx()` method | password |

- `setXxx()` prohibited → use meaningful method name `changeXxx()`
- Validation also performed when changing mutable fields

## 6. Domain Service

**When to use**: Business invariant verification requiring repository queries (e.g., duplicate ID check)

Design principles:
- **Pure class** — no framework annotations
- **Stateless** design: mediates domain object collaboration within the same BC
- Inject functional interfaces via constructor (e.g., `Predicate<String>`)
- Register with `@Bean` in Config class, inject repository method references
- Complex use-cases (cross-BC combinations) handled in Application Layer (Facade) via Client

```
// Domain Service (pure class)
public class LoginIdDuplicateValidator {
    private final Predicate<String> existsByLoginId;
    public LoginIdDuplicateValidator(Predicate<String> existsByLoginId) { ... }
    public void validate(String loginId) {
        if (existsByLoginId.test(loginId)) throw new CoreException(ErrorType.DUPLICATED);
    }
}

// Config registration
@Bean LoginIdDuplicateValidator validator(UserQueryRepository repo) {
    return new LoginIdDuplicateValidator(repo::existsByLoginId);
}
```

## 7. BaseEntity Constraints

- `id`: `final Long id = 0L` + `@GeneratedValue(IDENTITY)` → cannot set id directly
- `createdAt`, `updatedAt`: auto-managed via `@PrePersist`, `@PreUpdate`
- Soft delete: `deletedAt` field, `delete()`/`restore()` methods provided

## 8. Prohibited Actions

- Using framework annotations on domain models (`@Entity`, `@Service`, etc.)
- Direct DB access from domain models
- Using `setXxx()` methods → use `changeXxx()`
- Performing input normalization in Facade/Service (domain model responsibility)
- Using framework annotations on domain services
