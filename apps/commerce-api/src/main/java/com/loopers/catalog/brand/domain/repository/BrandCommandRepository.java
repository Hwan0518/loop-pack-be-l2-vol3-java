package com.loopers.catalog.brand.domain.repository;


import com.loopers.catalog.brand.domain.model.Brand;


public interface BrandCommandRepository {

	/**
	 * 브랜드 명령 리포지토리
	 * 1. 브랜드 저장
	 * 2. 브랜드 삭제 (soft delete)
	 */

	// 1. 브랜드 저장
	Brand save(Brand brand);

	// 2. 브랜드 삭제 (soft delete)
	void delete(Brand brand);

}
