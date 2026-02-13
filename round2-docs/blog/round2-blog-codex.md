</br>
</br>


> 아름다운 코드

</br>
</br>
</br>

난 도메인별 책임, 레이어별 책임을 상당히 많이 고민한다. </br>
깔끔하게 책임이 분리되고, 그에 알맞는 아름다운 코드가 작성되면 희열을 느끼기 때문이다.
</br>

이런 고민이 누군가에게는 상당히 쓸데없는 고민이고, 비효율적이라 보일 수도 있지만.. 어쩌겠는가? 내 성격이 그런걸..
</br>

그렇지만, 이런 고민은 비단 '아름다움'만을 위한 고민이 아니라는 것은 자신있게 말할 수 있다.
</br>

아름다운 코드란, 판단의 기준점을 시스템적으로 강제하여 일관성 있는 코드를 작성할 수 있게 해주고,
시간이 지나도 어디서 무엇을 고쳐야 하는지 빠르게 드러나게 해준다.
</br>

특히 도메인 정책이 커질수록 이 기준은 더 중요해진다.  
“이 검증은 어디서 해야 맞는가?” 같은 질문에 흔들리지 않으려면,  
모델과 레이어의 책임을 먼저 분리해두는 편이 훨씬 안정적이었다.
</br>

이번 글도 그 연장선에서 시작됐다.  
User 도메인을 다듬으면서 필드 정책 검증의 위치를 고민했고,  
그 과정에서 VO에 대한 작은 깨달음을 얻었다.
</br>

> VO에 대한 작은 깨달음
</br>

여기서부터가 이번에 가장 오래 붙잡고 있던 고민이었다.
</br>

필드값 정책 검증은 분명 비즈니스 정책인데, 그렇다고 User 같은 Model 안에서 전부 처리하려고 하니 어딘가 어색했다.  
검증의 중심이 User라는 집합보다, loginId, email, birthDate, password 같은 필드 값 자체에 더 가까웠기 때문이다.
</br>

내가 고민한 위치는 Domain Model, Domain Service, Application Service였다.  
각 레이어에 위치시킬 때 다음과 같은 문제가 발생했다.
</br>

### Domain Model

</br>

첫 번째로, Domain Model에 정책 검증을 위임하게 된다면, 행위 중심이 흐려진다는 문제가 발생한다.  
필드 정책은 해당 도메인 모델의 '아주 핵심적인 비즈니스 로직'이라 보기에 애매하고, '도메인 모델 전체'를 아우르는 중심적인 변경사항도 아니기 떄문인다.  
따라서 필드 정책을 모델 내부에 계속 쌓기 시작하면, 모델이 핵심 행위를 표현하는 객체라기보다 검증 조건 모음처럼 보이는 문제가 발생한다.
</br>

```java
public class User {

	private LoginId loginId;
	private Email email;


	public void validateFields() {
		// 로그인 ID 길이 정책 검증
		if (loginId == null || loginId.value().length() < 4) {
			throw new IllegalArgumentException("invalid loginId");
		}
		// 이메일 형식 정책 검증
		if (email == null || !email.value().contains("@")) {
			throw new IllegalArgumentException("invalid email");
		}
	}

}
```

</br>

### Domain Service

</br>

두 번째로, Domain Service에 정책 검증을 위임하면 역할 정의가 과해진다는 문제가 발생한다.  
Domain Service는 보통 여러 도메인 개념이 함께 엮일 때 사용하는데, 단일 필드 정책 검증을 위해 별도 서비스로 분리하는 것은 설계 밀도가 맞지 않았다.  
결국 “분리했다”는 형태만 남고, 책임의 설득력은 오히려 약해지는 느낌이었다.
</br>

```java
public class UserValidationDomainService {

	public void validateLoginId(String rawLoginId) {
		// 단일 필드(로그인 ID) 정책 검증
		if (rawLoginId == null || rawLoginId.trim().length() < 4) {
			throw new IllegalArgumentException("invalid loginId");
		}
	}

}
```

</br>

### Application Service

</br>

세 번째로, Application Service에 정책 검증을 두면 오케스트레이션 경계가 흐려진다는 문제가 발생한다.  
서비스 계층은 유스케이스 흐름 제어와 트랜잭션 경계에 집중해야 하는데, 필드 정책까지 함께 들고 가면 정책 책임이 점점 서비스로 쏠리게 된다.  
그 결과 서비스가 조합자가 아니라 정책 저장소처럼 커질 위험이 있었다.
</br>

```java

@Service
public class UserCommandService {

	public void signUp(UserSignUpInDto inDto) {
		// 서비스 계층에서 직접 로그인 ID 정책 검증
		if (inDto.loginId() == null || inDto.loginId().trim().length() < 4) {
			throw new IllegalArgumentException("invalid loginId");
		}
		// orchestration + persistence...
	}

}
```

</br>

결국 이런 문제점 때문에 다른 위치를 고민하게 되었고, 그때 VO에 대해서 생각하게 되었다.
</br>

### VO

</br>

나는 VO를 정말 말 그대로 ValueObject로 생각했다.  
'값' 자체로 어느정도 의미가 있고, 핵심 비즈니스 로직이 존재하는 부분(ex. 금액과 관련된 핵심 비즈니스 로직이 존재하는 Price, 여러 '주소'관련 필드들이 모여있는 Adress 등)  
만 VO로 생성해야하고, 그렇지 않다면 '오버엔지니어링'이거나 '과한 설계'라 생각했기에 vo 사용을 지양했다.
</br>

하지만 이번 고민을 통해 단순 필드만 존재하더라도 비즈니스적인 정책이 존재한다면, 그 정책은 VO가 직접 책임지는 편이 가장 자연스럽다는 결론에 도달했다.  
현재 코드에서도 LoginId.create, Name.create, Email.create, Birthdate.create처럼 값 경계에서 정책을 보장하고, 서비스는 이를 조합하는 형태로 역할이 정리된다.  
또 Password.validatePasswordPolicy처럼 다른 값과 연관된 규칙도 VO 중심으로 관리할 수 있어, 모델/서비스 책임 경계가 훨씬 선명해졌다.
</br>

```java
public record LoginId(String value) {

	public static LoginId create(String rawLoginId) {
		// 입력값 정규화
		String normalized = normalize(rawLoginId);
		// 정규화된 값 정책 검증
		validate(normalized);
		// 정책을 통과한 값으로 VO 생성
		return new LoginId(normalized);
	}


	private static String normalize(String loginId) {
		// 공백 제거 + 소문자 통일
		return loginId == null ? null : loginId.trim().toLowerCase();
	}


	private static void validate(String loginId) {
		// 로그인 ID 길이 범위 정책 검증
		if (loginId == null || loginId.length() < 4 || loginId.length() > 20) {
			throw new IllegalArgumentException("invalid loginId");
		}
	}

}
```

</br>

> 그런데 정말로 분리할 필요가 있을까?
</br>

여기서 한 번 더 고민이 생겼다.  
안그래도 Model을 분리하면서 관리 포인트가 늘었는데, VO를 적극적으로 도입하면서 더 많은 관리 포인트가 생긴 것이다.  
“방향은 이해했는데, 정말로 Domain Model과 Persistence Model을 분리해야 할까?”
</br>

분리의 장점이 분명한 건 맞다.  
책임 경계가 또렷해지고, 도메인 정책을 값과 모델에 명확히 배치할 수 있다.  
하지만 동시에 무시하기 어려운 비용도 생긴다.
</br>

### 코드량 증가

</br>

모델과 엔티티를 분리하면 매핑 코드, 변환 테스트, 관련 타입이 함께 늘어난다.  
작은 기능을 빠르게 구현해야 하는 시점에는 이 비용이 체감상 크게 다가온다.
</br>

### 추적 비용 증가

</br>

하나의 유스케이스를 따라갈 때도 Domain -> Mapper -> Entity 경로를 오가야 해서,  
처음 구조를 보는 사람은 진입 장벽을 느낄 수 있다.
</br>

### 과설계 리스크

</br>

도메인 복잡도가 아직 낮은 상태에서 과도하게 분리하면,  
얻는 이점보다 유지 비용이 더 크게 느껴질 수 있다.
</br>

그래서 이 질문은 결국 “분리가 옳으냐”가 아니라,  
“우리 도메인 복잡도와 변경 빈도에서 분리 비용을 감당할 가치가 있느냐”로 바뀌었다.
</br>

내 경우에는 User 도메인에서 필드 정책, 행위, 영속성 관심사를 계속 분리해보는 과정에서  
단기 생산성보다 장기 일관성과 안정성이 더 중요하다는 쪽으로 기울었다.  
즉, 불편함이 있더라도 분리 자체를 포기할 이유는 아니라고 판단했다.
</br>

> 두 가지 방식 비교
</br>

앞선 고민을 감으로만 끝내지 않기 위해, `Domain Model + Persistence Model 분리` 방식과 `Entity-only` 가정을 실제 테스트 코드 기준으로 비교했다.
</br>

### 비교 기준

</br>

- 도메인 단위 테스트 작성 난이도
- 정책 검증 책임의 명확성
- 변경 시 영향 범위
- 코드 추적/이해 비용
  </br>

### 요약

</br>

| 항목                            | 분리 방식 (Domain + Entity)     | Entity-only 가정               |
|-------------------------------|-----------------------------|------------------------------|
| 생성 픽스처 준비 호출 수                | 6회                          | 11회                          |
| 생성 테스트 단언 시 `toDomain()` 호출 수 | 0회                          | 4회                           |
| 유효성 실패 경로 호출 수                | 1회                          | 2회                           |
| 비밀번호 변경 표현 단계 수               | 1단계 (`user.changePassword`) | 2단계 (새 embeddable + 엔티티 재조립) |
| 계층 결합도(도메인 테스트 기준)            | 낮음                          | 높음                           |

</br>

해석:
</br>

- 생성 단계에서 Entity-only는 `Embeddable.fromDomain(...)` 준비가 추가되어 준비 코드가 누적된다.
- 단언 단계에서 Entity-only는 `toDomain()` 변환 호출이 늘어나, 비즈니스 의미보다 변환 코드가 먼저 보이기 쉽다.
- 유효성 실패 테스트는 차이가 작지만, Entity-only는 persistence 경유가 한 단계 더 들어간다.
- 행위 테스트에서 분리 방식은 도메인 메서드 호출 1회로 끝나지만, Entity-only는 재조립 단계가 추가된다.
- 결과적으로 Entity-only는 도메인 테스트가 persistence 타입에 더 강하게 결합된다.
  </br>

### 코드 예시 1) 유저 생성 테스트

</br>

분리 방식:
</br>

```java

@Test
@DisplayName("유저 생성 테스트: 도메인 타입만으로 픽스처 준비")
void createUser_withDomainModel() {
	User user = User.create(
		LoginId.create(RAW_LOGIN_ID),
		Password.from(ENCODED_PASSWORD),
		Name.create(VALID_NAME),
		Birthdate.create(VALID_BIRTH_DATE),
		Email.create(VALID_EMAIL)
	);

	assertAll(
		() -> assertThat(user.getLoginId().value()).isEqualTo(NORMALIZED_LOGIN_ID),
		() -> assertThat(user.getName().value()).isEqualTo(VALID_NAME),
		() -> assertThat(user.getBirthDate().value()).isEqualTo(VALID_BIRTH_DATE),
		() -> assertThat(user.getEmail().value()).isEqualTo(VALID_EMAIL)
	);
}
```

</br>

Entity-only 가정:
</br>

```java

@Test
@DisplayName("유저 생성 테스트: 도메인 테스트에서도 embeddable 준비가 필요")
void createUser_withEntityOnlyModel() {
	UserEntity entity = UserEntity.of(
		UserLoginIdEmbeddable.fromDomain(LoginId.create(RAW_LOGIN_ID)),
		UserPasswordEmbeddable.fromDomain(Password.from(ENCODED_PASSWORD)),
		UserNameEmbeddable.fromDomain(Name.create(VALID_NAME)),
		UserBirthdateEmbeddable.fromDomain(Birthdate.create(VALID_BIRTH_DATE)),
		UserEmailEmbeddable.fromDomain(Email.create(VALID_EMAIL))
	);

	assertAll(
		() -> assertThat(entity.getLoginId().toDomain().value()).isEqualTo(NORMALIZED_LOGIN_ID),
		() -> assertThat(entity.getName().toDomain().value()).isEqualTo(VALID_NAME),
		() -> assertThat(entity.getBirthDate().toDomain().value()).isEqualTo(VALID_BIRTH_DATE),
		() -> assertThat(entity.getEmail().toDomain().value()).isEqualTo(VALID_EMAIL)
	);
}
```

</br>

코드 예시를 보면, 분리 방식은 도메인 타입만으로 픽스처를 만들고 바로 도메인 의미를 검증할 수 있다.  
반면 Entity-only는 단위테스트에서도 `Embeddable` 생성과 `toDomain()` 변환을 계속 신경 써야 해서, 테스트 의도가 영속성 표현 코드에 묻히기 쉽다.
</br>

### 코드 예시 2) 비밀번호 변경 행위 테스트

</br>

분리 방식:
</br>

```java

@Test
@DisplayName("행위 테스트: 비밀번호 변경을 도메인 메서드 1회 호출로 검증")
void changePassword_withDomainMethod() {
	User user = User.create(
		LoginId.create(NORMALIZED_LOGIN_ID),
		Password.from(ENCODED_PASSWORD),
		Name.create(VALID_NAME),
		Birthdate.create(VALID_BIRTH_DATE),
		Email.create(VALID_EMAIL)
	);

	user.changePassword(NEW_ENCODED_PASSWORD);

	assertThat(user.getPassword().value()).isEqualTo(NEW_ENCODED_PASSWORD);
}
```

</br>

Entity-only 가정:
</br>

```java

@Test
@DisplayName("행위 테스트: 엔티티 변경 메서드가 없으면 재조립으로 검증")
void changePassword_withoutDomainObject_requiresRebuild() {
	UserEntity original = UserEntity.of(
		UserLoginIdEmbeddable.fromDomain(LoginId.create(NORMALIZED_LOGIN_ID)),
		UserPasswordEmbeddable.fromDomain(Password.from(ENCODED_PASSWORD)),
		UserNameEmbeddable.fromDomain(Name.create(VALID_NAME)),
		UserBirthdateEmbeddable.fromDomain(Birthdate.create(VALID_BIRTH_DATE)),
		UserEmailEmbeddable.fromDomain(Email.create(VALID_EMAIL))
	);

	UserEntity changed = UserEntity.of(
		original.getId(),
		original.getLoginId(),
		UserPasswordEmbeddable.fromDomain(Password.from(NEW_ENCODED_PASSWORD)),
		original.getName(),
		original.getBirthDate(),
		original.getEmail()
	);

	assertAll(
		() -> assertThat(changed.getLoginId().toDomain().value()).isEqualTo(NORMALIZED_LOGIN_ID),
		() -> assertThat(changed.getPassword().toDomain().value()).isEqualTo(NEW_ENCODED_PASSWORD),
		() -> assertThat(changed.getName().toDomain().value()).isEqualTo(VALID_NAME)
	);
}
```

</br>

여기서도 분리 방식은 `user.changePassword(...)` 호출 자체가 테스트 의도를 분명히 보여준다.  
반면 Entity-only는 상태 변경 검증을 위해 재조립 패턴을 써야 하므로, 행위 검증보다 구성 코드가 먼저 눈에 들어오는 문제가 생긴다.
</br>

### 코드 예시 3) 지연 로딩 전제로 인해 순수 단위테스트가 어려워지는 경우

</br>

```java

@Test
void readUserOrders_withoutTransaction_canFail() {
	UserEntity user = userRepository.findById(1L).orElseThrow();

	// orders가 LAZY라면 트랜잭션 경계 밖에서 접근 시 예외 가능
	assertThatThrownBy(() -> user.getOrders().size())
		.isInstanceOf(org.hibernate.LazyInitializationException.class);
}
```

</br>

이런 형태가 늘어나면, 도메인 규칙 테스트보다 “트랜잭션 안에서 돌았는가”를 먼저 맞추게 된다.  
결국 단위테스트가 인프라 실행 조건에 민감해진다.
</br>

### 코드 예시 4) 식별자/영속 상태를 먼저 맞추느라 테스트 의도가 흐려지는 경우

</br>

```java

@Test
void equals_behavior_depends_on_id_state() {
	UserEntity a = UserEntity.of(/* same fields */);
	UserEntity b = UserEntity.of(/* same fields */);

	// DB 식별자가 없는 신규 엔티티 상태에서는 동등성 기대가 흔들릴 수 있음
	assertThat(a.equals(b)).isFalse();

	// 저장 후 식별자가 부여되면 비교 결과 전제가 달라짐
	UserEntity savedA = userRepository.save(a);
	UserEntity savedB = userRepository.save(b);

	assertThat(savedA.getId()).isNotNull();
	assertThat(savedB.getId()).isNotNull();
}
```

</br>

이 경우 테스트가 “비즈니스 규칙이 맞는가”보다 “엔티티 상태가 어떤가”를 먼저 다루게 된다.  
즉, 테스트 가독성과 의도 전달력이 떨어지는 문제가 발생한다.
</br>

### 테스트 코드 작성으로 알아본 문제점

</br>

핵심은 “당장 깨지느냐”보다 “도메인 경계가 지속적으로 오염되느냐”다.
</br>

1. 도메인 테스트 목적 훼손  
   도메인 규칙 검증 테스트가 persistence 표현 검증까지 동시에 떠안게 된다.
   </br>

2. 행위 모델 약화  
   행위를 호출해 검증하는 대신, 재조립 패턴이 테스트 기본값이 되기 쉽다.
   </br>

3. 변경 전이 범위 확대  
   임베디드/컬럼/매핑 구조 변경이 도메인 테스트 수정으로 쉽게 전파된다.
   </br>

4. 테스트가 `@Transactional` 전제에 끌려가기 쉬움  
   엔티티 연관관계가 생기면 지연 로딩 프록시 초기화 타이밍까지 신경 써야 하므로, 순수 단위테스트가 인프라 전제에 기대기 쉽다.
   </br>

5. 식별자/영속 상태 전제가 단언보다 앞섬  
   엔티티는 신규/영속/분리 상태나 식별자 존재 여부가 중요한데, 이 전제를 맞추느라 테스트 핵심 의도가 흐려질 수 있다.
   </br>

> 결론
</br>

분리 방식 장점:
</br>

- 도메인/영속성 경계를 명확히 나눌 수 있다.
- 테스트가 비즈니스 의도 중심으로 읽힌다.
- 구조 변경 시 영향 범위를 비교적 예측하기 쉽다.
  </br>

분리 방식 단점:
</br>

- 매핑 코드와 타입 수가 증가한다.
- 초기 구현 속도가 느려질 수 있다.
  </br>

Entity-only 장점:
</br>

- 처음 구조를 따라갈 때 경로가 짧다.
- 매퍼 레이어 자체는 줄어든다.
  </br>

Entity-only 단점:
</br>

- 도메인 테스트가 persistence 표현에 결합된다.
- 준비/변환 보일러플레이트가 누적된다.
- DB 표현 변경이 도메인 테스트 수정으로 전파되기 쉽다.
  </br>

이번 비교에서 내가 특히 크리티컬하게 본 포인트는 네 가지였다.  
첫째, entity-only에서는 도메인 단위 테스트가 `Embeddable`/컬럼 구조에 직접 의존하게 되어 도메인 규칙 검증과 영속성 구조 검증이 섞인다.  
둘째, 생성/행위 테스트에서 준비·변환 코드가 누적된다. 실제 샘플에서도 생성 호출 수가 `6 -> 11`로 늘었고, 행위 테스트는 재조립 패턴이 추가되며 핵심 의도를 가리는 코드가 많아졌다.  
셋째, `BaseEntity` 라이프사이클 필드(`@PrePersist`, `@PreUpdate`)는 순수 단위 테스트에서 동작하지 않아 `createdAt/updatedAt`가 `null`일 수 있고, 타임스탬프
의존 로직이 늘수록 테스트 준비 비용이 커진다.  
넷째, `protected` 기본 생성자 경로를 통해 같은 패키지 테스트에서 팩토리 검증을 우회한 픽스처가 섞일 가능성도 생긴다.
</br>

물론 타협 가능한 지점도 있다.  
유효성 실패 테스트는 여전히 `LoginId.create` 같은 VO 생성 경계에서 실패하므로 복잡도 차이가 크지 않은 구간이 있다.  
또 entity-only는 매핑 레이어를 제거해 구조를 단순화하고, 매핑 누락 버그를 줄일 수 있다는 이점도 분명하다.  
팀이 통합 테스트 중심 전략이라면 단위 테스트 불편의 우선순위가 상대적으로 낮아질 수도 있다.
</br>

그래서 결론은 흑백이 아니다.  
현재 코드 기준에서 entity-only는 즉시 치명적 붕괴를 만드는 방식이라기보다, 도메인 행위 테스트에서 불편이 점진적으로 누적되는 구조에 가깝다.  
도메인 복잡도와 장기 리팩터링 빈도가 높다면 분리 방식이 유리하고, 단순 CRUD 중심이라면 일부 타협도 가능하다.
</br>

결국 내가 생각하는 '아름다운 코드'라는 것은, 단순히 아름답다에서 끝나는 것이 아니라, 시간이 지나도 '기준이 흔들리지 않는 구조'를 의미한다.  
이런 기준으로 바라본다면, 당장은 비용이 조금 더 들더라도 계속해서 model을 분리하는 방향으로 코드를 작성할 것 같다.
