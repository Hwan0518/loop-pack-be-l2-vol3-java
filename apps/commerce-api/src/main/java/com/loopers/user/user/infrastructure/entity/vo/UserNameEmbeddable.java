package com.loopers.user.user.infrastructure.entity.vo;


import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * 유저 이름 임베더블
 * - value: 이름
 */
@Embeddable
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNameEmbeddable {

	private String value;


	private UserNameEmbeddable(String value) {
		this.value = value;
	}


	public static UserNameEmbeddable of(String value) {
		return new UserNameEmbeddable(value);
	}

}
