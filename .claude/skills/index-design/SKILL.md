---
name: index-design
description: Database index design guidelines for JPA entities. Use when adding or reviewing composite indexes, analyzing query performance, or designing new table schemas.
---

# Index Design

## 1. 컬럼 순서 원칙 — 카디널리티 우선

복합 인덱스의 컬럼 순서는 다음 규칙을 따른다:

```
[카디널리티 높은 equality 컬럼] → [카디널리티 낮은 equality 컬럼] → [sort/range 컬럼]
```

| 순서 | 기준 | 이유 |
|:----:|------|------|
| 1st | 카디널리티가 가장 높은 equality 컬럼 | B-tree 첫 레벨 fan-out 균등화 |
| 2nd | 나머지 equality 컬럼 | 연속 탐색으로 range 축소 |
| Last | ORDER BY / range scan 대상 컬럼 | 인덱스 순서 = 정렬 순서 → filesort 제거 |

### 왜 카디널리티 순인가?

- 복합 인덱스에서 **모든 equality 컬럼이 쿼리에 사용되면**, 컬럼 순서와 무관하게 matching rows는 동일
- 하지만 카디널리티가 높은 컬럼이 선두에 오면 **B-tree 분기가 균등해져 인덱스 페이지 접근 효율이 향상**됨
- 카디널리티가 낮은 컬럼(예: `deleted_at` — 2값)이 선두에 오면 B-tree 첫 레벨이 편향됨

### 예시

```
쿼리: WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY created_at DESC

brand_id 카디널리티: 수십~수백 (높음)
deleted_at 카디널리티: 2 (NULL / timestamp, 낮음)

✅ (brand_id, deleted_at, created_at) — 카디널리티 높은 brand_id 선두
❌ (deleted_at, brand_id, created_at) — 카디널리티 낮은 deleted_at 선두
```

## 2. IS NULL과 인덱스

- MySQL에서 `IS NULL`은 **equality(ref)** 로 처리됨 (range가 아님)
- `deleted_at IS NULL`이 전체의 99%+여도 인덱스 사용 가능
- 단, 카디널리티가 2뿐이므로 **선두 컬럼으로는 비효율적** → equality 컬럼 중 후순위에 배치

## 3. 쿼리 조합별 인덱스 커버리지

조회 API에 정렬이 포함된 경우, **가능한 모든 WHERE + ORDER BY 조합**에 대해 인덱스를 설계한다.

```
예: 사용자/관리자 × 브랜드유무 × N개 정렬 = 2 × 2 × N개 인덱스
```

### 조합이 많아지는 경우

| 조건 | 인덱스 설계 |
|------|-----------|
| WHERE A AND B ORDER BY C | `(A, B, C)` — equality 2개 + sort 1개 |
| WHERE A ORDER BY C (B 없음) | `(A, C)` — 별도 2-column 인덱스 필요 |
| WHERE B ORDER BY C (A 없음) | `(B, C)` — 별도 2-column 인덱스 필요 |
| ORDER BY C (필터 없음) | `(C)` — 단일 컬럼 인덱스 필요 |

**중간 컬럼 스킵 시 정렬 불가**: `(A, B, C)` 인덱스에서 `WHERE A ORDER BY C`는 B를 건너뛸 수 없어 **filesort 발생**. 반드시 별도 `(A, C)` 인덱스 필요.

## 4. JPA Entity 선언 방식

```java
@Table(name = "table_name", indexes = {
    // 사용자 조회: WHERE brand_id = ? AND deleted_at IS NULL ORDER BY created_at DESC
    // 카디널리티: brand_id(높음) → deleted_at(낮음) → sort_col
    @Index(name = "idx_{table}_{col1}_{col2}_{col3}", columnList = "brand_id, deleted_at, created_at"),
})
```

### 네이밍 규칙

| 유형 | 패턴 | 예시 |
|------|------|------|
| 복합 인덱스 | `idx_{table}_{col1}_{col2}_{col3}` | `idx_read_brand_deleted_created` |
| 단일 인덱스 | `idx_{table}_{col}` | `idx_read_created` |

### 주석 규칙

- 각 인덱스 위에 **대상 쿼리 패턴**을 주석으로 명시
- 카디널리티 순서가 일반적이지 않은 경우 그 이유를 주석으로 설명

## 5. 인덱스 추가 체크리스트

인덱스를 추가하거나 리뷰할 때 다음을 확인한다:

- [ ] equality 컬럼이 range/sort 컬럼보다 앞에 위치하는가?
- [ ] equality 컬럼 간 카디널리티가 높은 순으로 배치되었는가?
- [ ] 선택적 필터(optional WHERE)가 빠진 쿼리에도 별도 인덱스가 존재하는가?
- [ ] 중간 컬럼 스킵으로 인한 filesort 발생 가능성을 검토했는가?
- [ ] 인덱스 주석에 대상 쿼리 패턴이 명시되어 있는가?
- [ ] 인덱스 수가 과도하지 않은가? (쓰기 비용 trade-off 검토)

## 6. Read Model 패턴에서의 인덱스

- Read Model 테이블은 **조회 전용**이므로 인덱스를 자유롭게 추가 가능
- 원본 테이블(쓰기 전용)에는 인덱스 추가 시 INSERT/UPDATE 비용 증가 고려
- Read Model에 비정규화된 컬럼(예: `brand_name`)이 있으면 JOIN 제거 가능 → 인덱스 효과 극대화
