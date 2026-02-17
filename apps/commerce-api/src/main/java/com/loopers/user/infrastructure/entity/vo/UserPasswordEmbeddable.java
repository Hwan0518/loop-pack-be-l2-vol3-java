package com.loopers.user.infrastructure.entity.vo;


import com.loopers.user.domain.model.vo.Password;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Embeddable
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPasswordEmbeddable {

	private String value;


	private UserPasswordEmbeddable(String value) {
		this.value = value;
	}


	public static UserPasswordEmbeddable fromDomain(Password password) {
		return new UserPasswordEmbeddable(password.value());
	}


	public Password toDomain() {
		return Password.from(value);
	}

}
