package com.loopers.user.user.domain.model;


import com.loopers.user.user.domain.model.vo.*;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.Objects;


@Getter
public class User {

	/**
	 * 유저
	 * - id: 유저 ID
	 * - loginId: 로그인 ID
	 * - password: 비밀번호 해시값
	 * - name: 이름
	 * - birthDate: 생년월일
	 * - email: 이메일
	 * - deletedAt: 삭제 일시 (soft delete)
	 */

	private Long id;
	private final LoginId loginId;
	private Password password;
	private final Name name;
	private final Birthdate birthDate;
	private final Email email;
	private ZonedDateTime deletedAt;


	// 생성자
	private User(Long id, LoginId loginId, Password password, Name name, Birthdate birthDate, Email email,
		ZonedDateTime deletedAt) {
		this.id = id; // null 허용
		this.loginId = Objects.requireNonNull(loginId, "loginId");
		this.password = Objects.requireNonNull(password, "password");
		this.name = Objects.requireNonNull(name, "name");
		this.birthDate = Objects.requireNonNull(birthDate, "birthDate");
		this.email = Objects.requireNonNull(email, "email");
		this.deletedAt = deletedAt; // null 허용
	}


	/**
	 * 도메인 로직
	 * 1. 유저 생성
	 * 2. 유저 재생성(Entity -> Model 매핑용도)
	 * 3. 비밀번호 변경
	 */

	// 1. 유저 생성 (신규 유저 생성)
	public static User create(LoginId loginId, Password password, Name name, Birthdate birthDate,
		Email email) {
		return new User(null, loginId, password, name, birthDate, email, null);
	}


	// 2. 유저 재생성 (Entity -> Model 매핑용도)
	public static User reconstruct(Long id, LoginId loginId, Password password, Name name, Birthdate birthDate,
		Email email, ZonedDateTime deletedAt) {
		return new User(id, loginId, password, name, birthDate, email, deletedAt);
	}


	// 3. 비밀번호 변경
	public void changePassword(String encodedNewPassword) {
		this.password = Password.from(encodedNewPassword);
	}

}
