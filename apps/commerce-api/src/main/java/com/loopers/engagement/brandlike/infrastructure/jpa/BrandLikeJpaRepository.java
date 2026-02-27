package com.loopers.engagement.brandlike.infrastructure.jpa;

import com.loopers.engagement.brandlike.infrastructure.entity.BrandLikeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface BrandLikeJpaRepository extends JpaRepository<BrandLikeEntity, Long> {

	/**
	 * 브랜드 좋아요 JPA 리포지토리
	 * 1. 사용자 ID와 대상 타입과 브랜드 ID로 좋아요 조회
	 * 2. 사용자 ID와 대상 타입과 브랜드 ID로 좋아요 존재 여부 확인
	 * 3. 사용자 ID와 대상 타입으로 좋아요 목록 조회 (페이지네이션)
	 * 4. 대상 타입과 브랜드 ID로 좋아요 전체 삭제
	 */

	// 1. 사용자 ID와 대상 타입과 브랜드 ID로 좋아요 조회
	Optional<BrandLikeEntity> findByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);

	// 2. 사용자 ID와 대상 타입과 브랜드 ID로 좋아요 존재 여부 확인
	boolean existsByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);

	// 3. 사용자 ID와 대상 타입으로 좋아요 목록 조회 (페이지네이션)
	Page<BrandLikeEntity> findByUserIdAndTargetType(Long userId, String targetType, Pageable pageable);

	// 4. 대상 타입과 브랜드 ID로 좋아요 전체 삭제
	void deleteAllByTargetTypeAndTargetId(String targetType, Long targetId);

}
