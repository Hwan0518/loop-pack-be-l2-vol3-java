package com.loopers.user.infrastructure.entity.vo;


import com.loopers.user.domain.model.vo.Name;
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


	public static UserNameEmbeddable fromDomain(Name name) {
		return new UserNameEmbeddable(name.value());
	}


	public Name toDomain() {
		return Name.from(value);
	}

}
