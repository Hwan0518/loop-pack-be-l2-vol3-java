# PG-Simulator 연동 스펙

## 기본 정보
- **Base URL**: `http://localhost:8082`
- **인증 헤더**: `X-USER-ID: {userId}` (모든 API 필수)
- **응답 래퍼**: `{ "meta": { "result": "SUCCESS"|"FAIL", "errorCode", "message" }, "data": { ... } }`

---

## API 1. 결제 요청
`POST /api/v1/payments`

**Request:**
```json
{
  "orderId": "1351039135",
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451",
  "amount": 5000,
  "callbackUrl": "http://localhost:8080/..."
}
```

| 필드 | 타입 | 필수 | 제약조건 |
|------|------|------|----------|
| `orderId` | String | O | 6자리 이상 |
| `cardType` | String | O | `SAMSUNG` / `KB` / `HYUNDAI` |
| `cardNo` | String | O | `xxxx-xxxx-xxxx-xxxx` 형식 |
| `amount` | Long | O | 양수 |
| `callbackUrl` | String | O | `http://localhost:8080`으로 시작 필수 |

**Response (data):**
```json
{
  "transactionKey": "20260315:TR:a6e308",
  "status": "PENDING",
  "reason": null
}
```

**실패 시**: HTTP 500 + `meta.result: "FAIL"` (요청 성공률 60% — 재시도/서킷브레이커 필요)

---

## API 2. 트랜잭션 조회
`GET /api/v1/payments/{transactionKey}`

**Response (data):**
```json
{
  "transactionKey": "20260315:TR:a6e308",
  "orderId": "1351039135",
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451",
  "amount": 5000,
  "status": "SUCCESS",
  "reason": "정상 승인되었습니다."
}
```

---

## API 3. 주문별 결제 목록 조회
`GET /api/v1/payments?orderId={orderId}`

**Response (data):**
```json
{
  "orderId": "1351039135",
  "transactions": [
    { "transactionKey": "...", "status": "SUCCESS", "reason": "..." }
  ]
}
```

---

## 콜백 (PG -> Commerce)
PG가 결제 처리 완료 후 `callbackUrl`로 **POST** 전송:

```json
{
  "transactionKey": "20260315:TR:a6e308",
  "orderId": "1351039135",
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451",
  "amount": 5000,
  "status": "SUCCESS",
  "reason": "정상 승인되었습니다."
}
```

| status | reason |
|--------|--------|
| `SUCCESS` | `"정상 승인되었습니다."` |
| `FAILED` | `"한도초과입니다. 다른 카드를 선택해주세요."` 또는 `"잘못된 카드입니다. 다른 카드를 선택해주세요."` |

---

## 연동 시 필수 고려사항
- **콜백 미수신 가능**: PG가 콜백 실패 시 재시도하지 않음 -> API 2/3으로 폴링 복구 필요
- **요청 지연**: 100~500ms -> 타임아웃 설정 필요
- **처리 지연**: 1~5초 후 콜백 도착 (비동기)
