package com.loopers.catalog.brand.infrastructure.repository;


import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.repository.BrandCommandRepository;
import com.loopers.catalog.brand.infrastructure.entity.BrandEntity;
import com.loopers.catalog.brand.infrastructure.jpa.BrandJpaRepository;
import com.loopers.catalog.brand.infrastructure.mapper.BrandEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;


@Repository
@RequiredArgsConstructor
public class BrandCommandRepositoryImpl implements BrandCommandRepository {

	// jpa
	private final BrandJpaRepository brandJpaRepository;
	// mapper
	private final BrandEntityMapper brandMapper;


	/**
	 * 브랜드 명령 리포지토리 구현체
	 * 1. 브랜드 저장
	 * 2. 브랜드 삭제 (soft delete)
	 */

	// 1. 브랜드 저장
	@Override
	public Brand save(Brand brand) {

		// 엔티티로 변환
		BrandEntity entity = brandMapper.toEntity(brand);

		// 저장
		BrandEntity savedEntity = brandJpaRepository.save(entity);

		// 결과 반환
		return brandMapper.toDomain(savedEntity);
	}


	// 2. 브랜드 삭제 (soft delete)
	@Override
	public void delete(Brand brand) {

		// 엔티티로 변환
		BrandEntity entity = brandMapper.toEntity(brand);

		// soft delete 처리
		entity.delete();

		// 저장
		brandJpaRepository.save(entity);
	}

}
