package com.loopers.catalog.product.domain.repository;


import com.loopers.catalog.product.domain.model.Product;

import java.util.List;


/**
 * 상품 Read Model 동기화 리포지토리
 * - write 경로에서 product_read_model 테이블을 동기화
 * - 구현체: ProductReadModelRepositoryImpl (infrastructure/repository/)
 *
 * 1. Read Model 저장 (생성/수정)
 * 2. Read Model soft delete (deletedAt 설정)
 * 3. 좋아요 수 증가
 * 4. 좋아요 수 감소
 * 5. 재고 업데이트
 * 6. 브랜드명 일괄 업데이트
 * 7. 브랜드 ID로 활성 상품 ID 목록 조회
 */
public interface ProductReadModelRepository {

	// 1. Read Model 저장 (생성/수정)
	void save(Product product, String brandName);

	// 2. Read Model soft delete (deletedAt 설정)
	void softDelete(Long productId);

	// 3. 좋아요 수 증가
	void increaseLikeCount(Long productId);

	// 4. 좋아요 수 감소
	void decreaseLikeCount(Long productId);

	// 5. 재고 업데이트
	void updateStock(Long productId, Long newStock);

	// 6. 브랜드명 일괄 업데이트
	void updateBrandName(Long brandId, String newBrandName);

	// 7. 브랜드 ID로 활성 상품 ID 목록 조회 (브랜드명 write-through용)
	List<Long> findActiveIdsByBrandId(Long brandId);

}
