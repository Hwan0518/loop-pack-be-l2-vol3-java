package com.loopers.user.infrastructure.entity;


import com.loopers.domain.BaseEntity;
import com.loopers.user.infrastructure.entity.vo.UserBirthdateEmbeddable;
import com.loopers.user.infrastructure.entity.vo.UserEmailEmbeddable;
import com.loopers.user.infrastructure.entity.vo.UserLoginIdEmbeddable;
import com.loopers.user.infrastructure.entity.vo.UserNameEmbeddable;
import com.loopers.user.infrastructure.entity.vo.UserPasswordEmbeddable;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * 유저 엔티티
 * - loginId: 로그인 ID
 * - activeLoginId: 활성 로그인 ID (유니크 제약용 가상 컬럼)
 * - password: 비밀번호
 * - name: 이름
 * - birthDate: 생년월일
 * - email: 이메일
 */
@Entity
@Table(name = "users", indexes = {
	@Index(name = "uk_active_login_id", columnList = "active_login_id", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity extends BaseEntity {

	@Embedded
	@AttributeOverride(
		name = "value",
		column = @Column(name = "login_id", nullable = false, unique = false, length = 20)
	)
	private UserLoginIdEmbeddable loginId;

	@Column(
		name = "active_login_id",
		columnDefinition = "VARCHAR(20) GENERATED ALWAYS AS (IF(deleted_at IS NULL, login_id, NULL)) STORED",
		insertable = false,
		updatable = false
	)
	private String activeLoginId;

	@Embedded
	@AttributeOverride(
		name = "value",
		column = @Column(name = "password", nullable = false)
	)
	private UserPasswordEmbeddable password;

	@Embedded
	@AttributeOverride(
		name = "value",
		column = @Column(name = "name", nullable = false, length = 50)
	)
	private UserNameEmbeddable name;

	@Embedded
	@AttributeOverride(
		name = "value",
		column = @Column(name = "birth_date", nullable = false)
	)
	private UserBirthdateEmbeddable birthDate;

	@Embedded
	@AttributeOverride(
		name = "value",
		column = @Column(name = "email", nullable = false, length = 254)
	)
	private UserEmailEmbeddable email;


	private UserEntity(
		Long id,
		UserLoginIdEmbeddable loginId,
		UserPasswordEmbeddable password,
		UserNameEmbeddable name,
		UserBirthdateEmbeddable birthDate,
		UserEmailEmbeddable email
	) {
		super(id);
		this.loginId = loginId;
		this.password = password;
		this.name = name;
		this.birthDate = birthDate;
		this.email = email;
	}


	public static UserEntity of(
		Long id,
		UserLoginIdEmbeddable loginId,
		UserPasswordEmbeddable password,
		UserNameEmbeddable name,
		UserBirthdateEmbeddable birthDate,
		UserEmailEmbeddable email
	) {
		return new UserEntity(
			id,
			loginId,
			password,
			name,
			birthDate,
			email
		);
	}

	public static UserEntity of(
		UserLoginIdEmbeddable loginId,
		UserPasswordEmbeddable password,
		UserNameEmbeddable name,
		UserBirthdateEmbeddable birthDate,
		UserEmailEmbeddable email
	) {
		return of(null, loginId, password, name, birthDate, email);
	}

}
