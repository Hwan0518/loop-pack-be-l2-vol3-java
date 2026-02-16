---
description: CQRS 프로젝트 맞춤형 기능 개발 워크플로우 (탐색 → 질문 → 검증 → TDD 구현 → 리뷰)
argument-hint: 구현할 기능 설명 (예: 상품 등록 API)
---

# Feature Development (CQRS Customized)

개발자가 새 기능을 구현하도록 돕는 체계적 워크플로우. 코드베이스를 먼저 이해하고, 질문하고, 기존 아키텍처 패턴에 맞게 설계를 검증한 후, TDD로 구현한다.

## Core Principles

- **질문 먼저**: 모호한 점, 엣지 케이스, 미명시 동작을 모두 식별. 가정하지 말고 질문하고 답변을 기다린다.
- **이해 후 행동**: 기존 코드 패턴을 먼저 읽고 파악
- **에이전트가 식별한 파일 읽기**: 에이전트 실행 후, 반환된 핵심 파일 목록을 반드시 직접 읽어서 상세 컨텍스트 확보
- **기존 패턴 준수**: 이 프로젝트는 CQRS 아키텍처가 확정됨. 새 아키텍처를 제안하지 말고, 기존 패턴에 맞는 구현 블루프린트를 작성
- **TDD 워크플로우**: Red → Green → Refactor 순서를 반드시 따른다
- **TodoWrite 사용**: 전 과정의 진행 상황 추적

## 프로젝트 아키텍처 요약 (CQRS)

**아키텍처 원칙**: `Application → Domain ← Infrastructure` (의존성 역전)

```
Controller → Facade(@Transactional) → Service → Repository(interface, domain/repository/) → RepositoryImpl → JpaRepository + Entity ↔ Domain
```

**레이어 규칙**:
- Facade는 Service만 호출 (Repository, Port, DomainService 직접 호출 금지)
- Service가 Repository, Port, DomainService를 호출
- DomainService는 Repository/Port 호출 금지 (Service가 데이터 전달), stateless 설계
- Domain Model은 순수 비즈니스 로직만 (외부 의존 없음)
- Repository Interface는 `domain/repository/`에 정의, 구현체는 `infrastructure/repository/`

**BC(Bounded Context) 경계**:
- `catalog`: Brand, Product (같은 BC → Service 직접 호출 가능)
- `engagement`: Like (다른 BC → Client/ACL로 접근)
- `ordering`: Order, OrderItem (다른 BC → Client/ACL로 접근)
- `user`: User (다른 BC → Client/ACL로 접근)

**Cross-BC 통신**:
- 동기: `application/client/{domain}/{Domain}Client` (인터페이스) + `infrastructure/acl/{domain}/{Domain}ClientImpl` (구현체)
- 비동기: 도메인 이벤트 + `@TransactionalEventListener` (최종적 일관성)

**도메인 모델 패턴**: `create()` + `reconstruct()` 팩토리, private 생성자, VO는 Java record

---

## Phase 1: Discovery

**Goal**: 무엇을 만들어야 하는지 이해

요청: $ARGUMENTS

**Actions**:
1. 전체 Phase에 대한 todo 목록 생성
2. 기능이 불명확하면 사용자에게 질문:
   - 어떤 문제를 해결하려는가?
   - 기능이 어떻게 동작해야 하는가?
   - 제약 조건이나 요구사항이 있는가?
3. 이해한 내용 요약 후 사용자에게 확인

---

## Phase 2: Codebase Exploration

**Goal**: 관련 기존 코드와 패턴을 CQRS 레이어별로 파악

**Actions**:
1. 2~3개의 `code-explorer` 에이전트를 병렬 실행. 각 에이전트는:
   - CQRS 레이어를 따라 코드를 포괄적으로 추적
   - 서로 다른 측면에 집중 (유사 기능 구현, 레이어 구조, 도메인 모델 패턴 등)
   - 반드시 읽어야 할 핵심 파일 5~10개 목록 포함

   **에이전트 프롬프트 예시**:
   - "[기능]과 유사한 기능을 찾아 Controller → Facade → Service → Repository → Entity → Domain 흐름으로 추적"
   - "[도메인 영역]의 아키텍처와 추상화를 매핑. 특히 DTO 변환 흐름(Request → InDto → Domain → OutDto → Response) 추적"
   - "[기존 기능]의 도메인 모델(팩토리 메서드, VO, 검증 로직)과 테스트 패턴 분석"

2. 에이전트가 반환한 모든 파일을 직접 읽어 심층 이해 확보
3. 발견된 패턴과 컨벤션의 종합 요약 제시

---

## Phase 3: Clarifying Questions

**Goal**: 설계 전 모든 모호성 해소

**CRITICAL: 이 Phase를 절대 건너뛰지 말 것.**

**Actions**:
1. 코드베이스 분석 결과와 원래 기능 요청 검토
2. 미명시 사항 식별:
   - 엣지 케이스 및 에러 처리 (어떤 ErrorType을 사용할지)
   - 도메인 모델 설계 (어떤 필드가 불변인지, VO로 분리할 필드는)
   - 유효성 검증 규칙 (null → empty → 길이 → 포맷 → 비즈니스)
   - 통합 포인트 및 Cross-BC 통신 필요성
   - 성능 요구사항
3. **모든 질문을 정리된 목록으로 사용자에게 제시**
4. **답변을 받은 후에만 다음 Phase로 진행**

---

## Phase 4: Pattern Validation & Blueprint

**Goal**: 기존 패턴에 맞는 구현 블루프린트 작성 및 검증

**NOTE**: 이 프로젝트는 CQRS 아키텍처가 확정되어 있음. "어떤 아키텍처를 쓸지"가 아니라, "확정된 아키텍처 내에서 어떻게 구현할지"를 설계.

**Actions**:
1. 2~3개의 `code-architect` 에이전트를 병렬 실행:
   - 에이전트 1: 기존 유사 도메인의 CQRS 구현 패턴을 분석하여 구현 블루프린트 작성
   - 에이전트 2: 도메인 모델 설계 (팩토리 메서드, VO, 검증 로직, Entity 매핑) 블루프린트 작성
   - 에이전트 3: 테스트 전략 블루프린트 (단위 테스트, 통합 테스트, E2E 테스트 구조)
2. 모든 블루프린트를 검토하여 하나의 통합 구현 계획으로 병합
3. 사용자에게 제시: 생성/수정할 파일 목록, 각 레이어별 구현 내용, 도메인 모델 설계, 테스트 전략
4. **사용자의 승인을 받은 후에만 구현 시작**

---

## Phase 5: TDD Implementation

**Goal**: TDD(Red → Green → Refactor)로 기능 구현

**사용자 명시적 승인 없이 구현을 시작하지 말 것.**

**Actions**:
1. 사용자의 명시적 승인 대기
2. 이전 Phase에서 식별된 모든 관련 파일 읽기
3. **TDD 사이클로 구현**:

   **Red Phase**: 실패하는 테스트 먼저 작성
   - 테스트는 3A 원칙(Arrange-Act-Assert)으로 작성
   - `@DisplayName`: `[메서드명()] 조건 -> 결과. 상세 설명` 형식
   - 예외 검증: `assertThrows` + `assertAll(errorType, message)` 패턴

   **Green Phase**: 테스트를 통과하는 최소한의 코드 작성
   - 오버엔지니어링 금지
   - 기존 코드베이스 컨벤션 엄격 준수

   **Refactor Phase**: 불필요한 코드 제거 및 품질 개선
   - unused import 제거
   - 모든 테스트가 통과해야 함

4. 진행 상황을 todo로 업데이트

---

## Phase 6: Quality Review

**Goal**: 코드 품질, 패턴 준수, 버그 검증

**Actions**:
1. 3개의 `code-reviewer` 에이전트를 병렬 실행:
   - 에이전트 1 (패턴 준수): CQRS 레이어 규칙, 도메인 모델 패턴, DTO 흐름 검증
   - 에이전트 2 (버그/정합성): 로직 오류, null 처리, 예외 처리, 테스트 커버리지
   - 에이전트 3 (컨벤션): DisplayName 형식, ErrorType 체크리스트, Entity 업데이트 패턴, 테스트 패턴
2. 결과 통합, 가장 심각한 이슈 식별
3. **발견 사항을 사용자에게 제시하고 조치 방법 질문** (지금 수정 / 나중에 수정 / 현재 상태로 진행)
4. 사용자 결정에 따라 이슈 처리

---

## Phase 7: Summary

**Goal**: 완료된 작업 정리

**Actions**:
1. 모든 todo 완료 처리
2. 요약:
   - 구현된 기능
   - 주요 결정 사항
   - 수정/생성된 파일
   - 다음 단계 제안 (추가 테스트, 문서화, 관련 API 등)

---
