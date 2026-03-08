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
| Service | 비즈니스 예외 발생 (Repository 결과를 받아 판단) | `existsByLoginId() → true → USER_ALREADY_EXISTS` |
| Facade | Authentication/authorization verification, use-case exceptions, 비가역적 부수 효과가 있는 INSERT race 시 `DataIntegrityViolationException` catch → 멱등 반환 | Missing auth header, Order idempotency |
| RepositoryImpl | 데이터 반환만 담당. **비즈니스 예외 발생 금지, `DataIntegrityViolationException` try-catch 금지** | `save()` → Entity/Domain 반환 |
| Controller | Delegates `@Valid` request body validation | Missing required fields |
| GlobalExceptionHandler | Converts common exceptions to HTTP responses | Automatic handling |

## 8. INSERT Unique Constraint Violation Handling

비즈니스 규칙상 중복 INSERT가 허용되지 않는 경우, DB UNIQUE constraint + 사전 조회(select-before-insert)로 처리한다.

- **사전 조회**: Service/Facade에서 비즈니스 검증 목적. 정상 흐름에서 의미 있는 에러/멱등 반환 제공
- **DB UNIQUE constraint**: 데이터 무결성 안전망. 항상 적용
- **RepositoryImpl에서 `DataIntegrityViolationException` try-catch 금지**: Repository는 데이터 반환만 담당
- **race 시 500 허용**: 부수 효과가 없거나 미미한 경우 (좋아요, 쿠폰, 회원가입)
- **race 시 `@Retryable`**: 부수 효과 없으나 수량 합산 누락 위험이 있는 경우 (장바구니). 재시도 시 새 TX에서 사전 조회가 기존 항목 발견 → 수량 합산으로 정상 처리
- **race 시 Facade try-catch**: 부수 효과가 크고 비가역적인 경우에만 (주문 — 재고 차감, 장바구니 삭제 등이 이미 커밋된 상태). **전제조건**: Facade에 `@Transactional`이 없고 쓰기 TX가 Service로 분리된 경우에만 동작 (`@Transactional` Facade 내부에서는 rollback-only → `UnexpectedRollbackException`)

## 9. Prohibited Actions

- Including stack traces in responses
- Exposing internal class names/package structures in error messages
- Swallowing exceptions with try-catch and ignoring them
- Using `RuntimeException` directly for business exceptions (use the common exception class)
- Overusing `Exception` or `Throwable` as catch-all
- RepositoryImpl에서 `DataIntegrityViolationException` catch하여 비즈니스 예외로 변환 (Repository는 데이터 반환만 담당)

## 9. Error Code Naming Conventions

- Enum constant names: `UPPER_SNAKE_CASE` (e.g., `INVALID_PASSWORD_FORMAT`)
- Error code strings: `UPPER_SNAKE_CASE` (e.g., `BRAND_NOT_FOUND`, `INVALID_PASSWORD_FORMAT`)
- Messages: written in Korean, **실제 서비스 사용자(비개발자) 관점**으로 작성
  - 사용자가 메시지를 보고 다음 행동을 알 수 있어야 한다 (예: "다시 시도해주세요", "고객센터에 문의해주세요")
  - 개발 용어 사용 금지: "충돌", "동시성", "제약 조건", "트랜잭션", "유니크", "중복 키" 등
  - 좋은 예: "장바구니 담기에 실패했습니다. 다시 시도해주세요."
  - 나쁜 예: "장바구니 항목 추가 중 충돌이 발생했습니다."
