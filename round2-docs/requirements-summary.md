# Round 2 요구사항 정리

---

## 공통 API 규칙

**목표: API 인증/식별 체계 수립**

**요구사항**
- 대고객 API는 `/api/v1` prefix 사용
- 어드민 API는 `/api-admin/v1` prefix 사용
- 유저 식별 헤더: `X-Loopers-LoginId` (로그인 ID), `X-Loopers-LoginPw` (비밀번호)
- 어드민 식별 헤더: `X-Loopers-Ldap` (값: `loopers.admin`)
- 유저는 타 유저의 정보에 직접 접근할 수 없음
- 인증/인가는 주요 스코프가 아니므로 별도 구현하지 않음 (헤더 기반 식별만)

**제안사항**
- 인증 실패 시 실패 사유를 구분하지 않고 단일 UNAUTHORIZED 응답 반환 (보안 고려)
- 어드민 LDAP 헤더 값 검증은 단순 문자열 비교로 충분

---

## 1. 유저 (Users)

### 1-1. 회원가입

**목표: 신규 유저 등록**

**요구사항**
- `POST /api/v1/users`
- 인증 불필요 (`user_required`: X)
- 유저 정보를 받아 신규 회원 등록

**제안사항**
- 입력 필드: loginId, password, name, birthDate, email
- loginId 중복 검증 필요 (Domain Service에서 처리)
- 입력값 정규화: loginId → trim().toLowerCase(), name/email → trim()
- 비밀번호는 인코딩하여 저장 (PasswordEncoder 활용)

---

### 1-2. 내 정보 조회

**목표: 로그인한 유저의 정보 조회**

**요구사항**
- `GET /api/v1/users/me`
- 인증 필요 (`user_required`: O)
- 헤더의 loginId/password로 유저 식별 후 정보 반환

**제안사항**
- 응답에 민감정보(password) 포함 금지
- 이름 마스킹 처리 고려 (예: 마지막 글자 마스킹)

---

### 1-3. 비밀번호 변경

**목표: 로그인한 유저의 비밀번호 변경**

**요구사항**
- `PUT /api/v1/users/password`
- 인증 필요 (`user_required`: O)
- 현재 비밀번호 확인 후 새 비밀번호로 변경

**제안사항**
- 현재 비밀번호 검증은 도메인 모델에 위임
- 새 비밀번호도 기존 비밀번호 정책(포맷, 길이 등) 적용

---

## 2. 브랜드 & 상품 - 대고객 (Brands / Products)

### 2-1. 브랜드 정보 조회

**목표: 단일 브랜드 정보 조회**

**요구사항**
- `GET /api/v1/brands/{brandId}`
- 인증 불필요 (`user_required`: X)
- brandId로 특정 브랜드 정보 반환

**제안사항**
- 고객에게 제공할 정보와 어드민 전용 정보를 구분하여 응답 설계
- 존재하지 않는 brandId에 대한 에러 처리

---

### 2-2. 상품 목록 조회

**목표: 상품 목록을 페이징 및 필터링하여 조회**

**요구사항**
- `GET /api/v1/products`
- 인증 불필요 (`user_required`: X)
- 쿼리 파라미터: `brandId` (브랜드 필터), `sort` (정렬), `page` (기본값 0), `size` (기본값 20)
- 정렬 기준 필수: `latest`
- 정렬 기준 선택: `price_asc`, `likes_desc`

**제안사항**
- 정렬 기준은 enum으로 관리하여 확장성 확보
- 유효하지 않은 sort 값에 대한 예외 처리
- brandId 미지정 시 전체 상품 조회

---

### 2-3. 상품 정보 조회

**목표: 단일 상품 상세 정보 조회**

**요구사항**
- `GET /api/v1/products/{productId}`
- 인증 불필요 (`user_required`: X)
- productId로 특정 상품 정보 반환

**제안사항**
- 고객용 응답에는 어드민 전용 필드 제외
- 존재하지 않는 productId에 대한 에러 처리

---

## 3. 브랜드 & 상품 - 어드민 (Admin)

### 3-1. 브랜드 목록 조회

**목표: 등록된 브랜드 목록을 페이징하여 조회**

**요구사항**
- `GET /api-admin/v1/brands?page=0&size=20`
- LDAP 인증 필요 (`ldap_required`: O)

**제안사항**
- 어드민 전용 필드 포함 (생성일, 수정일 등)

---

### 3-2. 브랜드 상세 조회

**목표: 단일 브랜드의 상세 정보 조회 (어드민)**

**요구사항**
- `GET /api-admin/v1/brands/{brandId}`
- LDAP 인증 필요 (`ldap_required`: O)

**제안사항**
- 대고객 조회보다 더 상세한 정보 제공 (내부 관리 정보 포함)

---

### 3-3. 브랜드 등록

**목표: 새로운 브랜드 등록**

**요구사항**
- `POST /api-admin/v1/brands`
- LDAP 인증 필요 (`ldap_required`: O)

**제안사항**
- 브랜드명 중복 검증 고려
- 필수 필드 Jakarta Validation 적용

---

### 3-4. 브랜드 정보 수정

**목표: 기존 브랜드 정보 수정**

**요구사항**
- `PUT /api-admin/v1/brands/{brandId}`
- LDAP 인증 필요 (`ldap_required`: O)

**제안사항**
- Entity 업데이트 패턴 사용 (dirty checking)
- 존재하지 않는 brandId에 대한 에러 처리

---

### 3-5. 브랜드 삭제

**목표: 브랜드 및 해당 브랜드의 상품 일괄 삭제**

**요구사항**
- `DELETE /api-admin/v1/brands/{brandId}`
- LDAP 인증 필요 (`ldap_required`: O)
- 브랜드 제거 시, 해당 브랜드의 상품들도 함께 삭제되어야 함

**제안사항**
- Soft delete 활용 (BaseEntity의 deletedAt)
- 트랜잭션 내에서 브랜드 + 상품 일괄 삭제 보장
- 연관 상품에 좋아요/주문이 있는 경우의 처리 정책 결정 필요

---

### 3-6. 상품 목록 조회 (어드민)

**목표: 등록된 상품 목록을 페이징하여 조회**

**요구사항**
- `GET /api-admin/v1/products?page=0&size=20&brandId={brandId}`
- LDAP 인증 필요 (`ldap_required`: O)

**제안사항**
- brandId 필터 지원
- 어드민 전용 필드 포함 (재고, 생성일, 수정일 등)

---

### 3-7. 상품 상세 조회 (어드민)

**목표: 단일 상품의 상세 정보 조회 (어드민)**

**요구사항**
- `GET /api-admin/v1/products/{productId}`
- LDAP 인증 필요 (`ldap_required`: O)

**제안사항**
- 재고, 내부 관리 정보 등 어드민 전용 상세 정보 제공

---

### 3-8. 상품 등록

**목표: 새로운 상품 등록**

**요구사항**
- `POST /api-admin/v1/products`
- LDAP 인증 필요 (`ldap_required`: O)
- 상품의 브랜드는 이미 등록된 브랜드여야 함

**제안사항**
- 브랜드 존재 여부 검증 (Domain Service 또는 Facade에서)
- 가격, 재고 등 필수 필드 검증

---

### 3-9. 상품 정보 수정

**목표: 기존 상품 정보 수정**

**요구사항**
- `PUT /api-admin/v1/products/{productId}`
- LDAP 인증 필요 (`ldap_required`: O)
- 상품의 브랜드는 수정할 수 없음

**제안사항**
- 브랜드 변경 시도 시 명확한 에러 응답
- Entity 업데이트 패턴 사용 (dirty checking)

---

### 3-10. 상품 삭제

**목표: 상품 삭제**

**요구사항**
- `DELETE /api-admin/v1/products/{productId}`
- LDAP 인증 필요 (`ldap_required`: O)

**제안사항**
- Soft delete 활용
- 해당 상품에 좋아요/주문이 있는 경우의 처리 정책 결정 필요

---

## 4. 좋아요 (Likes)

### 4-1. 상품 좋아요 등록

**목표: 특정 상품에 좋아요 등록**

**요구사항**
- `POST /api/v1/products/{productId}/likes`
- 인증 필요 (`user_required`: O)

**제안사항**
- 동일 유저의 동일 상품 중복 좋아요 방지
- 존재하지 않는 상품에 대한 에러 처리
- 상품의 좋아요 수 카운트 관리 방안 결정 (실시간 집계 vs 별도 컬럼)

---

### 4-2. 상품 좋아요 취소

**목표: 특정 상품의 좋아요 취소**

**요구사항**
- `DELETE /api/v1/products/{productId}/likes`
- 인증 필요 (`user_required`: O)

**제안사항**
- 좋아요하지 않은 상품에 대한 취소 요청 시 에러 처리
- 좋아요 수 카운트 감소 처리

---

### 4-3. 내가 좋아요 한 상품 목록 조회

**목표: 로그인한 유저가 좋아요한 상품 목록 조회**

**요구사항**
- `GET /api/v1/users/{userId}/likes`
- 인증 필요 (`user_required`: O)

**제안사항**
- 본인의 좋아요 목록만 조회 가능 (타 유저 접근 차단)
- 페이징 지원 고려
- 삭제된 상품의 좋아요 처리 정책 결정 필요

---

## 5. 주문 (Orders)

### 5-1. 주문 요청

**목표: 여러 상품을 한 번에 주문**

**요구사항**
- `POST /api/v1/orders`
- 인증 필요 (`user_required`: O)
- 요청 본문: `items` 배열 (각 항목에 `productId`, `quantity` 포함)
- 주문 시 상품 재고 확인 및 차감 보장
- 주문 정보에는 당시의 상품 정보가 스냅샷으로 저장되어야 함

**제안사항**
- 재고 부족 시 명확한 에러 응답 (어떤 상품이 부족한지)
- 동시성 제어: 재고 차감 시 race condition 방지 (비관적 락 또는 낙관적 락)
- 스냅샷 저장 필드: 상품명, 가격, 브랜드명 등 주문 시점 정보
- 결제 기능은 추후 추가 개발 예정

---

### 5-2. 유저의 주문 목록 조회

**목표: 로그인한 유저의 주문 목록을 기간별로 조회**

**요구사항**
- `GET /api/v1/orders?startAt=2026-01-31&endAt=2026-02-10`
- 인증 필요 (`user_required`: O)
- 기간 필터: `startAt`, `endAt`

**제안사항**
- 날짜 형식 검증 (ISO 8601)
- 페이징 지원 고려
- 본인의 주문 목록만 조회 가능

---

### 5-3. 단일 주문 상세 조회

**목표: 특정 주문의 상세 정보 조회**

**요구사항**
- `GET /api/v1/orders/{orderId}`
- 인증 필요 (`user_required`: O)

**제안사항**
- 본인의 주문만 조회 가능 (타 유저 주문 접근 차단)
- 주문 항목별 스냅샷 정보 포함
- 주문 상태 정보 포함 고려