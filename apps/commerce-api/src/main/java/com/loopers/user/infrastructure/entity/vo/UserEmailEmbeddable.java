package com.loopers.user.infrastructure.entity.vo;


import com.loopers.user.domain.model.vo.Email;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * 유저 이메일 임베더블
 * - value: 이메일
 */
@Embeddable
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEmailEmbeddable {

	private String value;


	private UserEmailEmbeddable(String value) {
		this.value = value;
	}


	public static UserEmailEmbeddable fromDomain(Email email) {
		return new UserEmailEmbeddable(email.value());
	}


	public Email toDomain() {
		return Email.from(value);
	}

}
