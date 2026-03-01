package com.loopers.user.user.infrastructure.entity.vo;


import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * 유저 비밀번호 임베더블
 * - value: 비밀번호 해시값
 */
@Embeddable
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPasswordEmbeddable {

	private String value;


	private UserPasswordEmbeddable(String value) {
		this.value = value;
	}


	public static UserPasswordEmbeddable of(String value) {
		return new UserPasswordEmbeddable(value);
	}

}
