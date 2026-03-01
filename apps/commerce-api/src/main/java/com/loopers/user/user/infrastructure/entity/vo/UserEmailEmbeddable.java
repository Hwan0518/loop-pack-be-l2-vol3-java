package com.loopers.user.user.infrastructure.entity.vo;


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


	public static UserEmailEmbeddable of(String value) {
		return new UserEmailEmbeddable(value);
	}

}
