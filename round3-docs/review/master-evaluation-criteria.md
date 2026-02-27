# Master Evaluation Criteria

## 0. 문서 메타

- 버전: v1.1
- 기준일: 2026-02-26
- 적용 범위: Round3 전체 재검토
- 판정 목표: 구현/문서/개발규약을 동시에 만족하는 결정완료형 평가 기준
- 개정 사유(v1.1): 도메인 책임 경계(단일 Aggregate 판정 vs 다중 Aggregate 협력 중재)와 `UNDECIDABLE` 우선 규칙 명확화

## 1. 기준 완성도 자가평가

- 기준 점수: 97/100
- 95점 이상 근거:
    - 하드게이트와 배점 분리로 합불/품질 수준을 동시 관리
    - 사용자 필수 체크리스트를 100% 통과 조건으로 강제
    - 비즈니스 웰논/엣지케이스를 PASS/FAIL 항목으로 구조화
    - CLAUDE.md + SKILL.md의 개발 규약을 별도 감사 트랙으로 강제
    - 애매한 항목의 분류(`UNDECIDABLE`)와 즉시 질문 규칙 명시

## 2. 평가 원칙

- 본 평가는 P0을 하드게이트 대상으로 삼는다.
- P1/P2는 하드게이트에서 제외하고 참고 감점/코멘트로만 처리한다.
- 모든 항목은 증거 기반으로만 판정한다.
- 증거 없는 PASS는 금지한다.

## 3. 판정 타입

- `PASS`: 기준 충족
- `FAIL`: 기준 미충족
- `FAIL-DOC`: 문서가 90% 이상 타당하고 구현이 문서와 불일치
- `UNDECIDABLE`: 우열 판단이 불가능하거나 증거가 불충분

## 4. 하드게이트 (하나라도 실패 시 최종 FAIL)

### G1. 체크리스트 100% 통과

- 8장(체크리스트 강제 항목) 전 항목 PASS 필수

### G2. 커버리지 게이트

- 테스트 제외 대상 제외 후 다음을 동시에 만족해야 한다.
    - Line coverage >= 90%
    - Branch coverage >= 90%
- 둘 중 하나라도 90% 미만이면 자동 FAIL

### G3. High 이슈 게이트

- High 심각도 항목에서 `FAIL` 또는 `FAIL-DOC` 1건 이상이면 자동 FAIL

### G4. 증거 게이트

- 항목당 코드/문서 근거 2개 이상 필수
- 근거 미달 시 PASS 금지, `UNDECIDABLE` 강제

## 5. 커버리지 산정 규칙

- 필수 표기:
    - 측정 도구/명령
    - 라인/브랜치 수치
    - 제외 목록
    - 제외 근거
    - 도메인별 수치(Product/Like/Order/Domain Service/Application)
- 제외 허용:
    - 부트스트랩/설정 클래스
    - 순수 매핑용 단순 DTO (비즈니스 로직 없음)
- 제외 금지:
    - Entity/VO/Domain Service/Application Service/Facade 등 비즈니스 로직 파일
- 제외 근거 미기재 또는 과도한 제외는 FAIL

## 6. 문서-구현 충돌 판정 프레임

- 외부 API 계약(엔드포인트/파라미터/응답/상태코드): requirements 우선
- 내부 설계 원칙(레이어/패키지/의존): CLAUDE.md + SKILL.md + 실제 코드 동시 대조
- 문서가 90%+ 타당하면 구현 불일치를 `FAIL-DOC`로 분류
- 우열 불가 시 `UNDECIDABLE`로 분리하고 점수 산정에서 제외

### 6.1 책임 경계 매핑 (강제)

| 규칙 유형 | 판정 소유권 | 권장 구현 위치 | 예시 |
|---|---|---|---|
| 단일 Aggregate 상태 전이/불변식 판정 | 단일 Aggregate | Entity/VO | `Product.decreaseStock()` |
| 단일 Aggregate 판정에 필요한 외부 facts 전달 | 판정은 단일 Aggregate, facts 준비는 Application | Application(조회/집계) + Entity/VO(판정) | `existsActiveByBrandId` 조회 후 `Brand.validateDeletable(hasActiveProducts)` |
| 다중 Aggregate 협력 중재 규칙 | 다수 Aggregate | Domain Service | 할당/매칭/충돌 해결/다중 일관성 판단 |
| 트랜잭션/락/오케스트레이션 | 유스케이스 흐름 | Facade/Application Service | 주문 생성 플로우 |

## 7. 100점 배점 (게이트 통과 후에만 계산)

- Product/Brand: 25
- Like: 20
- Order: 25
- Domain Service & Layer 책임: 15
- Architecture/Test/운영 리스크: 15
- 합격선: 80점 이상

## 8. 필수 체크리스트 (100% 통과 강제)

### 8.1 Product / Brand 도메인

- [ ] 상품 정보 객체는 브랜드 정보, 좋아요 수를 포함한다.
- [ ] 상품 정렬(`latest`, `price_asc`, `likes_desc`) 고려 조회 기능을 설계했다.
- [ ] 상품은 재고를 가지고 있고, 주문 시 차감할 수 있어야 한다.
- [ ] 재고 음수 방지 처리는 도메인 레벨에서 처리된다.

판정 보정:

- 정렬값은 의미가 동일하면 소문자/대문자(`LATEST` 등) 모두 허용한다.

### 8.2 Like 도메인

- [ ] 좋아요는 유저와 상품 간의 관계로 별도 도메인으로 분리했다.
- [ ] 상품 좋아요 수는 상품 상세/목록 조회에서 함께 제공된다.
- [ ] 단위 테스트에서 좋아요 등록/취소 흐름을 검증했다.

판정 보정:

- Product Like는 하드게이트 필수.
- Brand Like는 권고 항목으로 점수에 반영하되 하드게이트 실패로는 처리하지 않는다.

### 8.3 Order 도메인

- [ ] 주문은 여러 상품을 포함하며, 각 상품 수량을 명시한다.
- [ ] 주문 시 상품 재고 차감을 수행한다.
- [ ] 재고 부족 예외 흐름을 고려해 설계되었다.
- [ ] 단위 테스트에서 정상 주문/예외 주문 흐름을 모두 검증했다.

### 8.4 도메인 서비스

- [ ] 단일 Aggregate 상태 전이/불변식 판정은 Entity/VO에 배치되었다.
- [ ] 단일 Aggregate 판정에 필요한 외부 facts 조회/집계는 Application Layer에서 처리하고, 판정은 Domain 계층에 위임되었다.
- [ ] 다중 Aggregate 협력 중재 규칙은 상태 없는 Domain Service에 배치되었다.
- [ ] 복합 유스케이스의 트랜잭션/락/오케스트레이션은 Application Layer에 배치되었다.

해석 고정:

- 입력 타입(boolean/count)이 아니라 **판정 소유권**으로 책임 위치를 결정한다.
- 외부 조회/집계/계산이 완료된 정책 입력(facts)을 전달받아 단일 Aggregate를 판정하는 경우, Entity/VO 구현을 허용한다.
- 규칙 판정 자체가 다수 Aggregate 협력 중재(할당/매칭/충돌 해결/다중 일관성 판단)를 요구하면 Domain Service가 담당한다.
- Domain Service를 단순 분기 래퍼로 남용하거나, 다중 협력 중재 규칙을 Application if문으로 고정하면 FAIL.

### 8.5 소프트웨어 아키텍처 & 설계

- [ ] 프로젝트 구성은 `Application -> Domain <- Infrastructure` 기반이다.
- [ ] Application Layer는 도메인 객체를 조합해 흐름을 orchestration 한다.
- [ ] 핵심 비즈니스 로직은 Entity/VO/Domain Service에 위치한다.
- [ ] Repository Interface는 Domain Layer, 구현체는 Infra에 위치한다.
- [ ] 패키지는 계층 + 도메인 기준이다.
- [ ] 테스트는 외부 의존 분리(Fake/Stub/Mock) 가능한 구조다.

## 9. 비즈니스 로직 정밀 점검 (웰논 + 엣지케이스)

### 9.1 권한/보안/소유권

- GUEST 상태 변경(Command) 차단 여부
- 타인 리소스 접근 시 소유권 마스킹(404) 여부
- ADMIN/USER 권한 경계 준수 여부

### 9.2 멱등성

- 좋아요 중복 등록 재요청 처리
- 좋아요 미등록 취소 처리
- 동일 주문 재전송 시 기존 주문 재반환(중복 재고 차감 금지)
- 동일 삭제 재요청 처리 일관성

### 9.3 주문 원자성/동시성

- All-or-Nothing(부분 주문/부분 차감 금지)
- 재고 부족 시 409 및 전체 롤백
- 락 타임아웃/경합 시 409 + 롤백
- 동시 주문 시 재고 정합성 유지(락 전략 검증)

### 9.4 삭제/참조 정합성

- Product 삭제 후 Cart/Like 정리 이벤트 흐름
- Brand 삭제 후 BrandLike 정리 흐름
- 삭제된 리소스 참조 필터링/조회 정책 준수
- 주문 상세는 스냅샷으로 조회 가능

### 9.5 카탈로그 정책

- 브랜드 VISIBLE/HIDDEN 노출 정책 준수
- 브랜드 삭제 정책(visible 금지, 활성상품 0개) 준수
- 상품 삭제 정책(P0)과 주문 스냅샷 정책 일관성

### 9.6 API 계약/입력 검증

- 상태코드 계약 일치
- 파라미터/정렬값/수량 유효성 검증
- 에러코드/메시지 일관성

## 10. 개발 규약 감사 (CLAUDE.md + SKILL.md 기준)

### 10.1 계층 규약

- Controller -> Facade -> Service -> Repository 순서 강제
- 계층 건너뛰기 금지(Controller -> Service 직접 호출 금지)
- Facade는 Service만 호출

### 10.2 Service 규약

- Service -> Service 호출 금지(오케스트레이션은 Facade)
- Service 의존 허용 대상: Repository/Port/DomainService/EventPublisher

### 10.3 Domain 규약

- 비즈니스 규칙은 Entity/VO/Domain Service에 위치
- Domain Service는 무상태
- Domain Service의 Repository/Port 직접 호출 금지
- 도메인 모델에 프레임워크 의존 금지

### 10.4 Cross-BC 규약

- 동기 연동: Port + ACL
- 비동기 연동: Domain Event + Transactional Event Listener
- Port/QueryPort 네이밍 및 위치 규칙 준수

### 10.5 Query 규약

- 복잡 조회는 QueryPort + QueryPortImpl + QuerydslRepository 패턴 준수
- "Repository" 네이밍 오용 금지(예외: QuerydslRepository)

## 11. 평가 절차

- Step 1: 하드게이트 우선 판정
- Step 2: High 이슈 분류
- Step 3: 체크리스트 세부 판정
- Step 4: 비즈니스 웰논/엣지케이스 판정
- Step 5: 개발 규약 감사
- Step 6: 점수 산정(게이트 통과 시만)
- Step 7: `UNDECIDABLE` 목록 정리 및 즉시 질문

## 12. 결과 보고서 포맷 (강제)

- 섹션 순서:
    - Gate 결과
    - High Findings
    - 도메인별 점수표
    - Coverage 상세(라인/브랜치/제외 근거)
    - UNDECIDABLE + 사용자 질의 필요 항목
    - 개선 액션(우선순위/영향도)
- 각 finding 필수:
    - 판정 타입
    - 심각도
    - 증거 2개 이상(`파일:라인`)
    - 수정 권고

## 13. 애매한 항목 처리 규칙

- 아래에 해당하면 즉시 사용자 질의:
    - 요구사항 문서와 구현이 상충하고 어느 쪽이 우선인지 판단 불가
    - 체크리스트 문구가 현재 코드 관례와 충돌
    - 제외 커버리지 타당성이 불명확
    - 정책이 P0/P1 경계를 넘나드는 경우
- 8.4 책임 경계 해석이 문서 간 상충하면 `FAIL` 확정 전에 `UNDECIDABLE`로 분리하고 해석 질의 후 재판정한다.
- 해석이 확정되기 전에는 해당 항목에 대한 High 게이트 확정 판정을 보류한다.
- 질의 전 임의 확정 금지
