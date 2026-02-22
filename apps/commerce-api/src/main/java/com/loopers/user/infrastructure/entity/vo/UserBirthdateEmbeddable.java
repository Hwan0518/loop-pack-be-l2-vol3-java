package com.loopers.user.infrastructure.entity.vo;


import com.loopers.user.domain.model.vo.Birthdate;
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


	public static UserBirthdateEmbeddable fromDomain(Birthdate birthdate) {
		return new UserBirthdateEmbeddable(birthdate.value());
	}


	public Birthdate toDomain() {
		return Birthdate.from(value);
	}

}
