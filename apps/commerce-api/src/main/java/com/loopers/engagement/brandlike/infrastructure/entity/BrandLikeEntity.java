package com.loopers.engagement.brandlike.infrastructure.entity;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * 브랜드 좋아요 엔티티
 * - 같은 likes 테이블 사용, target_type = 'BRAND' 고정
 * - userId: 사용자 ID
 * - targetType: 좋아요 대상 타입 (BRAND 고정)
 * - targetId: 브랜드 ID
 */
@Entity
@Table(name = "likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandLikeEntity extends BaseEntity {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "target_type", nullable = false, length = 20)
	private String targetType;

	@Column(name = "target_id", nullable = false)
	private Long targetId;


	private BrandLikeEntity(Long userId, Long targetId) {
		this.userId = userId;
		this.targetType = "BRAND";
		this.targetId = targetId;
	}


	public static BrandLikeEntity of(Long userId, Long targetId) {
		return new BrandLikeEntity(userId, targetId);
	}

}
