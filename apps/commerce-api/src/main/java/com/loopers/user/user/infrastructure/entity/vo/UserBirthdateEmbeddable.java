package com.loopers.user.user.infrastructure.entity.vo;


import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;


/**
 * 유저 생년월일 임베더블
 * - value: 생년월일
 */
@Embeddable
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBirthdateEmbeddable {

	private LocalDate value;


	private UserBirthdateEmbeddable(LocalDate value) {
		this.value = value;
	}


	public static UserBirthdateEmbeddable of(LocalDate value) {
		return new UserBirthdateEmbeddable(value);
	}

}
