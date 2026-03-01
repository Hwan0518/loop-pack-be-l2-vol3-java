package com.loopers.catalog.product.application.facade;


import com.loopers.catalog.brand.application.service.BrandQueryService;
import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.product.application.dto.in.AdminProductCreateInDto;
import com.loopers.catalog.product.application.dto.in.AdminProductUpdateInDto;
import com.loopers.catalog.product.application.dto.out.AdminProductDetailOutDto;
import com.loopers.catalog.product.application.service.ProductCommandService;
import com.loopers.catalog.product.application.service.ProductQueryService;
import com.loopers.catalog.product.domain.model.Product;
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
	 */

	// 1. 상품 생성
	@Transactional
	public AdminProductDetailOutDto createProduct(AdminProductCreateInDto inDto) {

		// 브랜드 존재 확인 (같은 Catalog BC이므로 직접 호출)
		Brand brand = brandQueryService.getBrandById(inDto.brandId());

		// 상품 생성
		Product savedProduct = productCommandService.createProduct(inDto);

		// DTO 변환 (브랜드명 포함)
		return AdminProductDetailOutDto.from(savedProduct, brand.getName().value());
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

		// DTO 변환
		return AdminProductDetailOutDto.from(updatedProduct, brand.getName().value());
	}


	// 3. 상품 삭제
	@Transactional
	public void deleteProduct(Long id) {

		// 활성 상품 조회
		Product product = productQueryService.findActiveById(id);

		// 상품 삭제
		productCommandService.deleteProduct(product);

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

}
