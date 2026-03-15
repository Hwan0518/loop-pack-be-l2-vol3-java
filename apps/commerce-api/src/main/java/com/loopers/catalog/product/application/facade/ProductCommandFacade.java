package com.loopers.catalog.product.application.facade;


import com.loopers.catalog.brand.application.service.BrandQueryService;
import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.product.application.dto.in.AdminProductCreateInDto;
import com.loopers.catalog.product.application.dto.in.AdminProductUpdateInDto;
import com.loopers.catalog.product.application.dto.out.AdminProductDetailOutDto;
import com.loopers.catalog.product.application.service.ProductCommandService;
import com.loopers.catalog.product.application.service.ProductQueryService;
import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.model.enums.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class ProductCommandFacade {

	// service
	private final ProductCommandService productCommandService;
	private final ProductQueryService productQueryService;
	private final BrandQueryService brandQueryService;


	/**
	 * 상품 명령 파사드
	 * 1. 상품 생성
	 * 2. 상품 수정
	 * 3. 상품 삭제
	 * 4. 상품 재고 차감 (Cross-BC 전용 — ACL에서 호출)
	 * 5. 좋아요 수 증가 (Cross-BC 전용 — ACL에서 호출)
	 * 6. 좋아요 수 감소 (Cross-BC 전용 — ACL에서 호출)
	 */

	// 1. 상품 생성
	@Transactional
	public AdminProductDetailOutDto createProduct(AdminProductCreateInDto inDto) {

		// 브랜드 존재 확인 (같은 Catalog BC이므로 직접 호출)
		Brand brand = brandQueryService.getBrandById(inDto.brandId());

		// 상품 생성
		Product savedProduct = productCommandService.createProduct(inDto);

		// Read Model 동기화
		productCommandService.syncReadModel(savedProduct, brand.getName().value());

		// write-through: 상세 캐시 + 모든 정렬 ID 리스트
		productCommandService.refreshProductDetailCache(savedProduct.getId());
		productCommandService.refreshIdListCacheForAllSorts(savedProduct.getBrandId());

		// Read Model 재조회 (likeCount는 Read Model이 SoT)
		return productQueryService.getAdminProductDetail(savedProduct.getId());
	}


	// 2. 상품 수정
	@Transactional
	public AdminProductDetailOutDto updateProduct(Long id, AdminProductUpdateInDto inDto) {

		// 활성 상품 조회
		Product product = productQueryService.findActiveById(id);

		// 상품 수정
		Product updatedProduct = productCommandService.updateProduct(product, inDto);

		// 브랜드 조회 (브랜드명 포함 응답)
		Brand brand = brandQueryService.getBrandById(updatedProduct.getBrandId());

		// Read Model 동기화
		productCommandService.syncReadModel(updatedProduct, brand.getName().value());

		// write-through: 상세 캐시 + PRICE_ASC 정렬 ID 리스트 (가격 변경 영향)
		productCommandService.refreshProductDetailCache(id);
		productCommandService.refreshIdListCacheForSort(updatedProduct.getBrandId(), ProductSortType.PRICE_ASC);

		// Read Model 재조회 (likeCount는 Read Model이 SoT)
		return productQueryService.getAdminProductDetail(id);
	}


	// 3. 상품 삭제
	@Transactional
	public void deleteProduct(Long id) {

		// 활성 상품 조회
		Product product = productQueryService.findActiveById(id);

		// 상품 삭제
		productCommandService.deleteProduct(product);

		// 상세 캐시: evict (삭제된 상품이므로)
		productCommandService.deleteProductDetailCache(id);

		// ID 리스트: write-through (모든 정렬 — 삭제 상품 제거)
		productCommandService.refreshIdListCacheForAllSorts(product.getBrandId());

		// 상품 좋아요 정리 (Cross-BC 부수효과)
		productCommandService.deleteAllProductLikes(product.getId());

		// 장바구니 항목 정리 (Cross-BC 부수효과)
		productCommandService.deleteAllCartItems(product.getId());
	}


	// 4. 상품 재고 차감 (Cross-BC 전용 — ACL에서 호출)
	@Transactional
	public void decreaseStock(Long productId, Long quantity) {
		productCommandService.decreaseStock(productId, quantity);
	}


	// 5. 좋아요 수 증가 (Cross-BC 전용 — ACL에서 호출)
	@Transactional
	public void increaseLikeCount(Long productId) {
		productCommandService.increaseLikeCount(productId);
	}


	// 6. 좋아요 수 감소 (Cross-BC 전용 — ACL에서 호출)
	@Transactional
	public void decreaseLikeCount(Long productId) {
		productCommandService.decreaseLikeCount(productId);
	}

}
