package com.loopers.user.infrastructure.entity.vo;


import com.loopers.user.domain.model.vo.LoginId;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * 유저 로그인 ID 임베더블
 * - value: 로그인 ID
 */
@Embeddable
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserLoginIdEmbeddable {

	private String value;


	private UserLoginIdEmbeddable(String value) {
		this.value = value;
	}


	public static UserLoginIdEmbeddable fromDomain(LoginId loginId) {
		return new UserLoginIdEmbeddable(loginId.value());
	}


	public LoginId toDomain() {
		return LoginId.from(value);
	}

}
