# User Domain Model 분리 vs Entity-Only 테스트 복잡도 비교

## 1. 목적
- 현재 코드베이스의 `User` 도메인 모델(`domain/model`)과 `UserEntity`(`infrastructure/entity`) 분리 구조를 기준으로,
- "모델을 분리하지 않고 Entity 하나만 사용"하는 경우 도메인 단위 테스트 작성이 얼마나 달라지는지 현실적으로 비교한다.
- 비교 원칙: 억지로 불편하게 만들지 않고, 실제로 발생하는 차이만 기록한다.

## 2. 비교 전제
- 비교 대상 도메인: `user`
- 가정한 entity-only 형태: **현재 `UserEntity` 구조를 그대로 사용** (`@Entity + @Embedded` 유지)
- 제외한 가정: `String/LocalDate` 평탄화 Entity 시나리오는 본 문서 범위에서 다루지 않는다.
- 비교 범위: 도메인 단위 테스트만 (서비스/리포지토리/E2E 제외)
- 샘플 코드 위치:
  - `apps/commerce-api/src/test/java/com/loopers/user/comparison/UserDomainVsEntityOnlyComparisonTest.java`

## 3. 시나리오별 샘플 비교

### 3.1 유저 생성 테스트
현재 분리 구조 샘플:
```java
User user = User.create(
    LoginId.create(RAW_LOGIN_ID),
    Password.from(ENCODED_PASSWORD),
    Name.create(VALID_NAME),
    Birthdate.create(VALID_BIRTH_DATE),
    Email.create(VALID_EMAIL)
);
```

entity-only 가정 샘플:
```java
UserEntity entity = UserEntity.of(
    UserLoginIdEmbeddable.fromDomain(LoginId.create(RAW_LOGIN_ID)),
    UserPasswordEmbeddable.fromDomain(Password.from(ENCODED_PASSWORD)),
    UserNameEmbeddable.fromDomain(Name.create(VALID_NAME)),
    UserBirthdateEmbeddable.fromDomain(Birthdate.create(VALID_BIRTH_DATE)),
    UserEmailEmbeddable.fromDomain(Email.create(VALID_EMAIL))
);
```

관찰:
- entity-only 가정에서는 도메인 테스트에서도 `Embeddable` 타입을 함께 알아야 한다.
- 현재 코드 기준으로는 `VO -> Embeddable` 변환 단계가 매번 추가된다.

### 3.2 유효성 실패 테스트 (로그인 ID)
현재 분리 구조 샘플:
```java
assertThrows(CoreException.class, () -> LoginId.create("ab"));
```

entity-only 가정 샘플:
```java
assertThrows(CoreException.class,
    () -> UserLoginIdEmbeddable.fromDomain(LoginId.create("ab")));
```

관찰:
- 핵심 실패 지점은 여전히 `LoginId.create` 이다.
- 즉, 이 시나리오에서의 복잡도 증가는 크지 않다.
- 다만 테스트가 persistence 타입 경로를 같이 가지게 되어 관심사가 섞인다.

### 3.3 비밀번호 변경 행위 테스트
현재 분리 구조 샘플:
```java
user.changePassword(NEW_ENCODED_PASSWORD);
assertThat(user.getPassword().value()).isEqualTo(NEW_ENCODED_PASSWORD);
```

entity-only 가정 샘플:
```java
UserEntity changed = UserEntity.of(
    original.getId(),
    original.getLoginId(),
    UserPasswordEmbeddable.fromDomain(Password.from(NEW_ENCODED_PASSWORD)),
    original.getName(),
    original.getBirthDate(),
    original.getEmail()
);
```

관찰:
- 현재 `UserEntity`는 도메인 변경 메서드가 없어, 테스트에서도 재조립(rebuild) 방식으로 상태 변경을 표현하게 된다.
- 이 경우 "행위 검증"보다 "구조 재구성" 코드가 더 눈에 띈다.

## 4. 정량 스냅샷 (샘플 코드 기준)

| 지표 | 현재 분리 구조 | entity-only 가정 | 차이 |
|------|----------------|------------------|------|
| 생성 픽스처 준비 시 호출 수 | 6 (`User.create` + VO 5개) | 11 (`UserEntity.of` + `Embeddable.fromDomain` 5개 + VO 5개) | +5 |
| 생성 테스트 단언에서 `toDomain()` 호출 수 | 0 | 4 | +4 |
| 유효성 실패 경로 호출 수 | 1 (`LoginId.create`) | 2 (`LoginId.create` + `UserLoginIdEmbeddable.fromDomain`) | +1 |
| 비밀번호 변경 표현 단계 수 | 1 (`user.changePassword`) | 2 (새 비밀번호 embeddable 생성 + `UserEntity` 재조립) | +1 |

해석:
- 복잡도 차이는 "테스트 로직 자체"보다 "준비/변환 코드"에서 더 크게 나타난다.
- 특히 생성/행위 테스트에서 누적 차이가 커지고, 유효성 실패 테스트에서는 차이가 작다.

## 5. 현실적인 불편/복잡 포인트
1. 픽스처 준비 코드 증가
- 도메인 테스트인데도 `Embeddable` 생성 코드가 반복된다.

2. 단언(Assert) 가독성 저하
- `entity.getX().toDomain().value()` 형태가 자주 등장해 핵심 의도(비즈니스 규칙) 파악이 느려진다.

3. 계층 경계 혼합
- 도메인 테스트가 persistence 타입에 의존하게 되어, 단위 테스트의 관심사가 넓어진다.

4. 리팩터링 전이 범위 증가
- 컬럼/임베디드 구조 변경 시 도메인 테스트까지 함께 수정할 가능성이 높아진다.

5. JPA 라이프사이클 필드(`createdAt`/`updatedAt`) null 가능성
- `BaseEntity`의 타임스탬프 세팅은 `@PrePersist`, `@PreUpdate` 훅에서만 실행된다.
- 그래서 순수 단위 테스트에서 entity-only 모델을 직접 생성해 쓰면 타임스탬프가 `null`일 수 있다.
- 현재는 즉시 문제 없더라도, 이후 DTO/도메인 규칙이 타임스탬프를 참조하면 테스트 픽스처 준비 비용이 늘어난다.

6. `protected` 기본 생성자에 의한 검증 우회 가능성
- 현재 `UserEntity`는 JPA 요구사항 때문에 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 가진다.
- 같은 패키지 테스트에서는 기본 생성자로 엔티티를 만들어 팩토리 경로 검증을 우회할 수 있다.
- 항상 버그로 이어지는 것은 아니지만, 잘못된 테스트 픽스처가 섞일 가능성을 높인다.

## 6. 균형 관점: entity-only의 현실적 장점
1. 매핑 레이어 제거
- Domain ↔ Entity 매핑 코드와 관련 테스트를 줄일 수 있어, 매핑 누락/오타 버그 가능성이 감소한다.

2. 추적 경로 단순화
- 하나의 모델만 보면 되므로, 작은 기능에서는 코드 이동 추적 비용이 줄어든다.

3. 클래스 수 감소
- 도메인/영속성 분리로 생기는 부가 타입(Mapper, Embeddable 등)을 줄일 수 있다.

## 7. 과장하지 않고 보면 "덜 불편한" 부분
1. 유효성 실패 검증 자체는 VO가 이미 강하게 검증하므로 entity-only에서도 큰 차이가 없을 수 있다.
2. 팀이 원래 통합 테스트 중심이라면, 단위 테스트의 불편이 상대적으로 덜 중요할 수 있다.

## 8. 결론
### 8.1 Critical 포인트
1. (상) 도메인 단위 테스트의 persistence 결합 증가
- entity-only로 가면 도메인 테스트가 `Embeddable`/컬럼 구조에 직접 의존하게 되어, 도메인 규칙 검증과 영속성 구조 검증이 섞인다.

2. (상) 생성/행위 테스트의 준비/변환 코드 누적
- 샘플 기준으로 생성 시 호출 수가 증가하고(`6 -> 11`), 행위 테스트도 재조립 패턴이 추가되어 핵심 의도를 가리는 보일러플레이트가 늘어난다.

3. (중) `BaseEntity` 라이프사이클 필드 관리 포인트
- 순수 단위 테스트에서는 `@PrePersist/@PreUpdate`가 동작하지 않아 `createdAt/updatedAt`가 `null`일 수 있다.
- 향후 타임스탬프 의존 로직이 늘면 테스트 준비 비용과 방어 코드가 같이 늘어난다.

4. (중) `protected` 기본 생성자에 의한 검증 우회 가능성
- 같은 패키지 테스트에서 팩토리 검증 경로를 우회한 픽스처 작성 가능성이 생긴다.

### 8.2 비교적 타협 가능한 부분
1. (중) 유효성 실패 테스트 복잡도 차이는 제한적
- 실패 지점이 여전히 VO 생성 경계(`LoginId.create` 등)에 있으므로, 이 영역은 체감 차이가 작다.

2. (중) 매핑 레이어 제거로 단순화 이점 존재
- Domain ↔ Entity 매핑 코드/테스트가 사라져 구조 단순화와 매핑 버그 감소 효과를 얻을 수 있다.

3. (하) 팀이 통합 테스트 중심이면 단위 테스트 불편의 우선순위가 낮아질 수 있음
- 테스트 전략이 통합 중심이라면 entity-only의 단위 테스트 단점이 상대적으로 덜 치명적일 수 있다.

최종적으로 현재 코드 기준에서는, entity-only가 "즉시 치명적 붕괴"를 만들기보다는 **도메인 행위 테스트에서 불편이 점진적으로 누적되는 구조**에 가깝다.  
따라서 도메인 복잡도/장기 리팩터링 빈도가 높다면 분리 방식이 유리하고, 단순 CRUD 중심이면 일부 타협도 가능하다.
