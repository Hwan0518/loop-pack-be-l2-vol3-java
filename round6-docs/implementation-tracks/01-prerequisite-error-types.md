# 선행 작업 — ErrorType 추가

> **실행 시점**: Track A, B 시작 전에 먼저 완료
> **포함 Step**: 1
> **완료 후**: Track A (`02-track-a`)와 Track B (`03-track-b`) 병렬 시작 가능

---

## Step 1. ErrorType 추가

**대상 파일**: `support/common/error/ErrorType.java`

| ErrorType | HttpStatus | message |
|-----------|-----------|---------|
| `ORDER_NOT_PAYABLE` | 400 | 주문이 결제 가능한 상태가 아닙니다. |
| `PAYMENT_NOT_FOUND` | 404 | 결제 정보를 찾을 수 없습니다. |
| `PAYMENT_ALREADY_IN_PROGRESS` | 409 | 이미 결제가 진행 중입니다. |
| `PG_REQUEST_FAILED` | 502 | PG 결제 요청에 실패했습니다. |
| `PG_SERVICE_UNAVAILABLE` | 503 | PG 서비스를 일시적으로 사용할 수 없습니다. |
| `PG_TIMEOUT` | 504 | PG 응답 시간이 초과되었습니다. |
| `INVALID_CARD_TYPE` | 400 | 지원하지 않는 카드 타입입니다. |
| `INVALID_CARD_NO` | 400 | 잘못된 카드번호 형식입니다. |

**테스트**: `ErrorTypeTest.errorTypeProvider()`에 케이스 추가 + `hasSize(N)` 업데이트

**근거**: Part 1 §7
