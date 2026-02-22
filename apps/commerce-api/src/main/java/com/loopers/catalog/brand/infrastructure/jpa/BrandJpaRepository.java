package com.loopers.catalog.brand.infrastructure.jpa;


import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.infrastructure.entity.BrandEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface BrandJpaRepository extends JpaRepository<BrandEntity, Long> {

	/**
	 * 브랜드 JPA 리포지토리
	 * 1. ID로 활성 브랜드 엔티티 조회
	 * 2. 활성 브랜드 목록 조회 (페이지네이션)
	 * 3. ID와 노출 상태로 활성 브랜드 엔티티 조회
	 * 4. 노출 상태별 활성 브랜드 목록 조회 (페이지네이션)
	 */

	// 1. ID로 활성 브랜드 엔티티 조회
	Optional<BrandEntity> findByIdAndDeletedAtIsNull(Long id);

	// 2. 활성 브랜드 목록 조회 (페이지네이션)
	Page<BrandEntity> findAllByDeletedAtIsNull(Pageable pageable);

	// 3. ID와 노출 상태로 활성 브랜드 엔티티 조회
	Optional<BrandEntity> findByIdAndVisibleStatusAndDeletedAtIsNull(Long id, VisibleStatus visibleStatus);

	// 4. 노출 상태별 활성 브랜드 목록 조회 (페이지네이션)
	Page<BrandEntity> findAllByVisibleStatusAndDeletedAtIsNull(VisibleStatus visibleStatus, Pageable pageable);

}
