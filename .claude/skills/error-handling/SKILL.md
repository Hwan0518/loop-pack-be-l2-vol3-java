---
name: error-handling
description: Error handling patterns and procedures for adding new error types. Use when adding new business exceptions or modifying error response structures.
---

# Error Handling

## 1. Error Handling Flow

```
Domain/Service code
  → throw CoreException(ErrorType.XXX)
    → GlobalExceptionHandler catches automatically
      → Returns ErrorResponse(code, message)
```

- All business exceptions are handled via **common exception class + error type enum** combination
- GlobalExceptionHandler requires no modification — adding a new error type is automatically handled

## 2. Error Response Schema

```json
{
  "code": "<error code string>",
  "message": "<user-facing error message>"
}
```

- HTTP status codes are managed in the error type enum
- **Never expose** stack traces in responses
- Do not expose internal implementation details in error messages

## 3. Error Type Enum Structure

```
ErrorType {
    HttpStatus status;    // HTTP status code
    String code;          // Error code (e.g., "BRAND_NOT_FOUND")
    String message;       // Default error message
}
```

## 4. Procedure for Adding New Error Types

### 4.1 Steps

1. Add new value to `ErrorType` enum
   ```
   NEW_ERROR_TYPE(HttpStatus.BAD_REQUEST, "NEW_ERROR_TYPE", "에러 메시지")
   ```
2. Use in domain code
   ```
   throw new CoreException(ErrorType.NEW_ERROR_TYPE)
   ```
3. (Optional) If custom message is needed
   ```
   throw new CoreException(ErrorType.NEW_ERROR_TYPE, "custom message")
   ```

### 4.2 Test Checklist

- [ ] Add new value to `ErrorType` enum
- [ ] Add test case to `ErrorTypeTest.errorTypeProvider()`
- [ ] Update `hasSize(N)` to N+1 in `ErrorTypeTest.enumConstantCount()`
- [ ] Write unit test for the business logic that throws this error
- [ ] Add error response verification in E2E test

## 5. Validation Error Handling

- Automatically handled when `@Valid` annotation validation fails
- `MethodArgumentNotValidException` → `400 BAD_REQUEST` returned automatically
- No additional code required — framework handles it

## 6. Authentication Error Pattern

```
Controller: @RequestHeader(required = false) → allows null
  → Facade validates null/blank
    → Returns single UNAUTHORIZED response
```

- **Security principle**: Do not differentiate authentication failure reasons (missing ID and wrong password return identical response)
- Password verification is delegated to the domain model

## 7. Error Handling Responsibilities by Layer

| Layer | Responsibility | Example |
|-------|---------------|---------|
| Domain Model | Throw exceptions on business rule violations | Password format validation failure |
| Domain Service | 여러 도메인 객체 간 비즈니스 불변식 검증 (Repository 미호출, Service가 데이터 전달) | 복합 도메인 규칙 검증 |
| Facade | Authentication/authorization verification, use-case exceptions | Missing auth header |
| Controller | Delegates `@Valid` request body validation | Missing required fields |
| GlobalExceptionHandler | Converts common exceptions to HTTP responses | Automatic handling |

## 8. Prohibited Actions

- Including stack traces in responses
- Exposing internal class names/package structures in error messages
- Swallowing exceptions with try-catch and ignoring them
- Using `RuntimeException` directly for business exceptions (use the common exception class)
- Overusing `Exception` or `Throwable` as catch-all

## 9. Error Code Naming Conventions

- Enum constant names: `UPPER_SNAKE_CASE` (e.g., `INVALID_PASSWORD_FORMAT`)
- Error code strings: `UPPER_SNAKE_CASE` (e.g., `BRAND_NOT_FOUND`, `INVALID_PASSWORD_FORMAT`)
- Messages: written in Korean, user-friendly (no internal terminology)
