# TODO: likes 기반 Read Model likeCount 재집계 배치

## 상황

`products.like_count` 컬럼을 제거하고, `likes` 테이블을 좋아요 수의 단일 SoT(Source of Truth)로 확립했다.
`product_read_model.like_count`는 유일한 비정규화 projection이며, 좋아요 생성/삭제 시 원자적 카운터(`+1`/`-1`)로 동기화된다.

## 문제

원자적 카운터 방식은 정상 흐름에서는 정확하지만, 다음 상황에서 drift가 발생할 수 있다:

1. **TX 부분 실패**: `likes` INSERT는 성공했으나 `product_read_model.like_count` UPDATE가 실패한 경우
2. **수동 데이터 보정**: 운영 중 likes 테이블을 직접 INSERT/DELETE한 경우
3. **버그에 의한 누적 오차**: 카운터 증감 로직의 edge case 누락 (예: 동시성 극단 상황)

drift 발생 시 자동 복구 수단이 없으면, `product_read_model.like_count`가 실제 좋아요 수와 영구적으로 불일치한다.

## 이유

- `likes` 테이블이 SoT이므로, `SELECT target_id, COUNT(*) FROM likes GROUP BY target_id`가 정확한 좋아요 수
- 현재 TTL 기반 캐시 안전망(2~3분)은 캐시 불일치만 해소하며, DB 레벨 drift는 해소하지 않음
- 배치로 주기적 재집계를 수행하면 drift를 자동 보정할 수 있음

## 개선 방안

`commerce-batch` 모듈에 Spring Batch Job을 추가하여 likes 테이블 기반으로 Read Model likeCount를 재집계한다.

### 배치 흐름

```
1. SELECT target_id AS product_id, COUNT(*) AS like_count FROM likes GROUP BY target_id
2. UPDATE product_read_model SET like_count = {집계값} WHERE id = {product_id}
3. 변경된 상품의 상세 캐시 write-through (선택)
```

### 실행 주기

- 일 1회 (새벽 시간대) 또는 수동 트리거
- 운영 이슈 발생 시 즉시 실행 가능하도록 API 트리거도 고려

### 주의사항

- 배치 실행 중 좋아요 생성/삭제가 동시에 발생할 수 있으므로, 최종 UPDATE는 `SET like_count = {집계값}`으로 덮어쓰기
- 대량 상품의 경우 chunk 단위 처리 (예: 100건씩)
- 배치 실행 로그에 변경 전후 차이(drift량)를 기록하여 모니터링

## 근거

- 이벤트 소싱 없이 카운터 기반 projection을 사용하는 시스템에서는 주기적 재집계가 업계 표준 안전망
- Netflix, Instagram 등도 카운터 기반 비정규화 + 주기적 재집계 패턴을 사용
- 배치 비용이 낮고 (단일 GROUP BY 쿼리), 효과가 높음 (drift 완전 해소)

## 우선순위

**낮음** — 현재 원자적 카운터 + TX 보장으로 정상 운영 중. 운영 규모가 커지거나 drift 관측 시 도입.

## 관련 파일

- `ProductReadModelJpaRepository` — `increaseLikeCount()`, `decreaseLikeCount()` (현재 카운터 방식)
- `ProductCommandService` — 좋아요 쓰기 경로
- `docs/todo/cache-event-driven-refresh.md` — 캐시 갱신 이벤트 기반 전환 TODO
