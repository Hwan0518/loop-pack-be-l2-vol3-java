# Track A — Payment 도메인 + PG 인프라

> **병렬 실행**: Track B (`03-track-b`)와 **동시 진행 가능**
> **선행 조건**: `01-prerequisite-error-types.md` 완료
> **후행 트랙**: Track C (`04-track-c-merge`) — Track A + B 완료 후 시작
> **포함 Step**: 3, 4, 8, 9, 10, 11

---

## Step 3. Payment 도메인 모델

**선행**: Step 1 (선행 작업에서 완료)

**신규 파일** (`payment/payment/` BC):
- `domain/model/Payment.java` — `create()`, `reconstruct()`, `updateTransactionKey()`, `fail()`, `succeed()`
- `domain/model/enums/PaymentStatus.java` — REQUESTED, SUCCESS, FAILED + 전이 검증
- `domain/model/enums/CardType.java` — SAMSUNG, KB, HYUNDAI

**테스트 (Red 먼저)**:
- `PaymentTest` — create() 검증, 상태 전이(succeed/fail), transactionKey 업데이트
- `PaymentStatusTest` — 허용/금지 전이
- `CardTypeTest` — enum 값 확인

**근거**: Part 1 §2.1, §2.2, §2.3

---

## Step 4. Payment 인프라 (Entity, Mapper, Repository)

**선행**: Step 3

**신규 파일**:
- `infrastructure/entity/PaymentEntity.java` — JPA Entity (`BaseEntity` 상속)
- `infrastructure/mapper/PaymentEntityMapper.java` — `@Component`, `toEntity()` + `toDomain()`
- `domain/repository/PaymentCommandRepository.java` — interface: `save(Payment)`
- `domain/repository/PaymentQueryRepository.java` — interface: `findById()`, `findByOrderIdAndStatus()`, `findByTransactionKey()`, `findRequestedByOrderId()`, `existsRequestedByOrderId()`, `findRequestedPaymentsCreatedBefore(LocalDateTime threshold)`
- `infrastructure/repository/PaymentCommandRepositoryImpl.java`
- `infrastructure/repository/PaymentQueryRepositoryImpl.java`
- `infrastructure/jpa/PaymentJpaRepository.java`

**테스트**:
- `PaymentEntityMapperTest` — toEntity/toDomain 변환
- Repository 통합 테스트 — save, 조회 동작 확인

**근거**: Part 1 §2.1, CLAUDE.md §4.8

---

## Step 8. DTO 정의 (Step 3 이후 시작 가능, Step 9/10과 병렬)

**선행**: Step 3

**신규 파일** (`payment/payment/`):

| 유형 | 파일 | 내용 |
|------|------|------|
| Request | `interfaces/web/request/PaymentCreateRequest.java` | orderId, cardType, cardNo + @Valid |
| InDto | `application/dto/in/PaymentCreateInDto.java` | orderId, cardType, cardNo |
| OutDto | `application/dto/out/PaymentOutDto.java` | `from(Payment)` |
| Response | `interfaces/web/response/PaymentResponse.java` | `from(PaymentOutDto)` |

**PG 연동 DTO**:
| 유형 | 파일 | 내용 |
|------|------|------|
| PG Request | `infrastructure/pg/dto/PgPaymentRequest.java` | orderId, cardType, cardNo, amount, callbackUrl |
| PG Response | `infrastructure/pg/dto/PgPaymentResponse.java` | meta + data (transactionKey, status, reason) |
| Callback | `interfaces/web/request/PaymentCallbackRequest.java` | transactionKey, orderId, status, reason 등 7필드 |

**PG 요청 필수 계약**:
- `X-USER-ID` 헤더: PG가 모든 API에 필수로 요구. Payment 생성 시의 userId를 문자열로 전달
- `callbackUrl`: `http://localhost:8080/api/v1/payments/callback` — application.yml의 `payment.pg.callback-url` 설정값으로 관리
- `base-url`: `http://localhost:8082` — application.yml의 `payment.pg.base-url` 설정값으로 관리

**근거**: Part 1 §5, CLAUDE.md §4.9

---

## Step 9. RestTemplate + 타임아웃 설정 (독립, Step 8과 병렬)

**선행**: 없음 (독립)

**신규 파일**: `payment/payment/support/config/PgRestTemplateConfig.java`

```
@Configuration → @Bean RestTemplate pgRestTemplate()
- ConnectionTimeout: 1s
- ReadTimeout: 3s
```

**근거**: Part 2 P2-1, P2-2

---

## Step 10. Resilience4j 설정 (독립, Step 8/9와 병렬)

**선행**: 없음 (독립)

**수정 파일**: `build.gradle.kts` — resilience4j-spring-boot3, spring-boot-starter-aop 의존성 추가

**신규 파일 또는 application.yml 설정**:

CircuitBreaker (`pgCircuitBreaker`):
- failure-rate-threshold: 50
- slow-call-duration-threshold: 1s
- slow-call-rate-threshold: 80
- sliding-window-type: COUNT_BASED
- sliding-window-size: 10
- minimum-number-of-calls: 5
- wait-duration-in-open-state: 30s
- permitted-number-of-calls-in-half-open-state: 3

Retry (`pgRetry`):
- max-attempts: 3
- wait-duration: 200ms
- exponential-backoff-multiplier: 2
- enable-randomized-wait: true
- retry-exceptions: PgRequestFailedException, PgConnectionTimeoutException
- ignore-exceptions: PgReadTimeoutException, CallNotPermittedException

**근거**: Part 2 P2-4, P2-6

---

## Step 11. PG Gateway (PG 호출 + Resilience 적용)

**선행**: Step 8, 9, 10

**신규 파일**:
- `payment/payment/application/port/out/client/pg/PgPaymentGateway.java` — interface
- `payment/payment/infrastructure/pg/PgPaymentGatewayImpl.java` — `@Component`

**PgPaymentGatewayImpl 구현**:
- `requestPayment()`: RestTemplate POST → PG 성공 응답 파싱 / 실패 시 예외 변환
- `getPaymentByTransactionKey()`: RestTemplate GET → 트랜잭션 조회
- `getPaymentsByOrderId()`: RestTemplate GET → 주문별 조회

**Resilience 적용**: `Retry(CircuitBreaker(requestPayment()))` 데코레이터 패턴
- Retry scope는 PG HTTP 호출만 감쌈 (Payment 생성은 바깥)

**PG 예외 매핑**:
| PG 상황 | 예외 | Retry |
|---------|------|:-----:|
| HTTP 500 | `PgRequestFailedException` | O |
| Connection timeout | `PgConnectionTimeoutException` | O |
| Read timeout | `PgReadTimeoutException` | X |

**RestTemplate 예외 변환 로직**:
Spring RestTemplate은 Connection timeout과 Read timeout 모두 `ResourceAccessException`으로 감싸므로, PG Gateway에서 cause를 검사하여 커스텀 예외로 변환한다:

```java
try {
    restTemplate.postForEntity(...);
} catch (ResourceAccessException e) {
    if (e.getCause() instanceof ConnectException) {
        throw new PgConnectionTimeoutException(e);  // Retry 대상
    }
    if (e.getCause() instanceof SocketTimeoutException) {
        throw new PgReadTimeoutException(e);         // Retry 제외
    }
    throw e;
}
```

**신규 예외 클래스**:
- `PgConnectionTimeoutException extends RuntimeException` — Connection timeout, Retry 대상
- `PgReadTimeoutException extends RuntimeException` — Read timeout, Retry 제외
- `PgRequestFailedException extends RuntimeException` — PG HTTP 500, Retry 대상

**테스트**: Mock 서버(MockRestServiceServer) 기반 단위 테스트
- PG 성공 응답 파싱
- PG 실패 (HTTP 500) 응답 처리
- 타임아웃 예외 처리
- 서킷 OPEN 시 CallNotPermittedException

**근거**: Part 2 §3, §6.1

---

## Track A 완료 산출물

| 산출물 | Track C에서 사용 |
|--------|----------------|
| ErrorType 8종 | Step 12, 14, 15 |
| Payment 도메인 (Payment, PaymentStatus, CardType) | Step 12, 13, 14 |
| Payment Repository (Command + Query) | Step 12, 13 |
| DTO (Request, InDto, OutDto, Response, PG DTO) | Step 14, 15, 16 |
| PG Gateway (Retry + CB 적용) | Step 14, 17, 18 |
| Resilience4j 설정 | Step 12 (서킷 상태 확인) |
