# implementation-plan.md 리뷰 결과 (2026-02-18)

## 1) 리뷰 범위
- 대상 문서: `round3-docs/implementation-plan.md`
- 기준 문서: `docs/design/01-requirements-v2.md`, `docs/design/02-sequence-diagrams.md`, `docs/design/03-class-diagram.md`, `docs/design/04-erd-claude.md`
- 보조 규칙: `CLAUDE.md`, `.claude/skills/*`

기준 우선순위는 `docs/design/*`를 1순위로 적용했다. `CLAUDE.md`와 `.claude/skills/*` 위배는 보조 감점으로 반영했다.

## 2) 평가 기준 및 가중치
- 요구사항 정합성: 60점
- 아키텍처/컨벤션 준수: 25점
- 실행 가능성/검증 가능성: 15점

## 3) 핵심 발견사항 (심각도 순)

### Critical
1. Hard Delete/삭제불가 테이블의 `deleted_at` 정책 충돌
- 근거
  - 계획: `round3-docs/implementation-plan.md:18`, `round3-docs/implementation-plan.md:323`, `round3-docs/implementation-plan.md:370`, `round3-docs/implementation-plan.md:432`
  - 기준: `docs/design/04-erd-claude.md:63`, `docs/design/04-erd-claude.md:64`, `docs/design/04-erd-claude.md:261`, `docs/design/04-erd-claude.md:283`, `docs/design/04-erd-claude.md:305`
- 문제
  - 계획은 Like/Cart/Order 계열에 BaseEntity를 공통 적용해 `deleted_at` 컬럼을 허용한다.
  - ERD 기준은 Hard Delete(`likes`, `cart_items`)와 삭제불가(`orders`, `order_items`)에 `deleted_at` 미포함을 명시한다.
- 영향
  - DB 스키마/엔티티/쿼리 조건이 설계와 어긋나고, 삭제 정책 검증 기준이 흔들린다.
- 권고
  - BaseEntity 분리 또는 엔티티별 매핑 분리로 `deleted_at` 비포함을 보장해야 한다.

### Major
2. 주문 목록 API의 날짜 필터(`startAt`, `endAt`) 명세 누락
- 근거
  - 기준: `docs/design/01-requirements-v2.md:823`, `docs/design/01-requirements-v2.md:827`, `docs/design/01-requirements-v2.md:828`, `docs/design/01-requirements-v2.md:834`, `docs/design/01-requirements-v2.md:836`
  - 계획: `round3-docs/implementation-plan.md:422`, `round3-docs/implementation-plan.md:426`
- 문제
  - 요구사항의 날짜 필터/경계 규칙(Asia/Seoul 포함)을 계획이 인터페이스 수준에서 고정하지 않았다.
- 영향
  - 구현자 판단에 따라 API 계약이 달라질 수 있고, E2E 기대치가 불안정해진다.
- 권고
  - `OrderQueryService`/Controller 시그니처에 `startAt`, `endAt`, timezone 경계 규칙을 명시하라.

3. 좋아요 목록 API의 페이지네이션 계약 누락
- 근거
  - 기준: `docs/design/01-requirements-v2.md:729`, `docs/design/01-requirements-v2.md:736`, `docs/design/01-requirements-v2.md:737`
  - 계획: `round3-docs/implementation-plan.md:284`, `round3-docs/implementation-plan.md:314`, `round3-docs/implementation-plan.md:318`
- 문제
  - 계획은 `target` 필터만 명시하고 `page`, `size` 계약을 서비스/리포지토리 인터페이스에 고정하지 않았다.
- 영향
  - 구현 시 목록 형태가 list/page로 갈라질 가능성이 높다.
- 권고
  - LikeQuery 계층에 `PageCriteria/PageResult` 기반 시그니처를 명시하라.

4. 관리자 브랜드 상세 조회 API가 계획에서 빠져 있음
- 근거
  - 기준: `docs/design/01-requirements-v2.md:621`, `docs/design/01-requirements-v2.md:640`
  - 계획: `round3-docs/implementation-plan.md:222`
- 문제
  - Admin Query Controller 설명이 목록 GET만 포함한다.
- 영향
  - P0 필수 엔드포인트 누락 가능성이 높다.
- 권고
  - `GET /api-admin/v1/brands/{brandId}`를 명시적으로 추가하라.

5. `likes_desc` 구현 전략이 기준 문서와 비정합
- 근거
  - 계획: `round3-docs/implementation-plan.md:17`, `round3-docs/implementation-plan.md:246`, `round3-docs/implementation-plan.md:257`
  - 기준: `docs/design/02-sequence-diagrams.md:408`, `docs/design/04-erd-claude.md:228`, `docs/design/04-erd-claude.md:242`
- 문제
  - 계획은 Product에 `likeCount` 필드 + 이벤트 동기화를 도입하지만, 기준 문서는 Repository(QueryDSL) 정렬과 현재 ERD 컬럼 구조를 전제로 한다.
- 영향
  - 스키마 변경/이벤트 정합성 관리가 추가되어 복잡도와 실패 시나리오가 증가한다.
- 권고
  - (A) QueryDSL 집계 정렬로 유지하거나, (B) `like_count` 도입 시 ERD/이벤트 실패 보정 전략까지 설계 문서에 함께 고정하라.

### Minor
6. Cross-BC 포트 네이밍이 보조 규칙과 충돌
- 근거
  - 보조 규칙: `CLAUDE.md:340`, `.claude/skills/layered-architecture/SKILL.md:105`
  - 계획: `round3-docs/implementation-plan.md:290`, `round3-docs/implementation-plan.md:345`, `round3-docs/implementation-plan.md:399`, `round3-docs/implementation-plan.md:400`, `round3-docs/implementation-plan.md:401`
- 문제
  - `...Validator`, `...Reader`, `...Manager`는 보조 규칙의 `...Port` 네이밍과 불일치한다.
- 영향
  - 코드베이스 일관성과 검색성이 떨어진다.
- 권고
  - `LikeTargetPort`, `CartProductPort`, `OrderProductPort`, `OrderStockPort`, `OrderCartItemPort` 형태로 통일하라.

7. 인증 검증 위치가 문서 규칙과 부분 불일치
- 근거
  - 보조 규칙: `CLAUDE.md:209`, `CLAUDE.md:210`
  - 계획: `round3-docs/implementation-plan.md:230`, `round3-docs/implementation-plan.md:315`, `round3-docs/implementation-plan.md:316`
- 문제
  - 일부는 Controller 검증, 일부는 Facade 검증으로 혼재된다.
- 영향
  - 인증 실패 처리 책임이 분산되고 테스트 패턴이 흔들린다.
- 권고
  - Facade 검증 원칙 또는 기존 코드 패턴 중 하나로 단일화하라.

## 4) 점수 산정

### 요구사항 정합성 (60점 만점): 42점
- P0 범위 정합: 19/20
- API 계약 정합: 12/20
- 정책/데이터 정합: 11/20

### 아키텍처/컨벤션 준수 (25점 만점): 18점
- 레이어/경계/네이밍 규칙: 18/25

### 실행 가능성/검증 가능성 (15점 만점): 12점
- 병렬 전략/테스트 전략은 우수하나, 일부 API 계약이 결정 불충분

## 5) 최종 점수

**72 / 100**

## 6) 필수 수정 체크리스트 (우선순위)
1. Like/Cart/Order 계열 `deleted_at` 정책을 ERD와 일치시킬 것 (`docs/design/04-erd-claude.md` 기준).
2. 주문 목록 조회에 `startAt`, `endAt`, 경계/시간대 규칙을 명시할 것.
3. 좋아요 목록 조회의 `page`, `size` 계약을 서비스/리포지토리 시그니처에 명시할 것.
4. `GET /api-admin/v1/brands/{brandId}`를 계획에 명시할 것.
5. `likes_desc` 구현 전략을 QueryDSL 집계 또는 ERD 확장 중 하나로 공식화할 것.
6. Cross-BC 포트 네이밍 규칙(`...Port`)을 통일할 것.
7. 인증 검증 책임 레이어(Controller vs Facade)를 단일 원칙으로 확정할 것.

## 7) 총평
- 강점
  - 단계별 병렬 전략, ArchUnit 도입, TDD 강제, 멱등성/동시성 포인트가 잘 정리되어 있다.
- 결론
  - 실행 계획의 뼈대는 견고하지만, API/데이터 계약의 일부 누락과 정책 충돌(특히 `deleted_at`) 때문에 즉시 구현에 들어가기엔 리스크가 있다.
