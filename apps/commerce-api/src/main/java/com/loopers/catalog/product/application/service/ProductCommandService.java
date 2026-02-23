package com.loopers.catalog.product.application.service;


import com.loopers.catalog.product.application.dto.in.AdminProductCreateInDto;
import com.loopers.catalog.product.application.dto.in.AdminProductUpdateInDto;
import com.loopers.catalog.product.domain.event.ProductDeletedEvent;
import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.repository.ProductCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class ProductCommandService {

	// repository
	private final ProductCommandRepository productCommandRepository;
	// event
	private final ApplicationEventPublisher eventPublisher;


	/**
	 * 상품 명령 서비스
	 * 1. 상품 생성
	 * 2. 상품 수정
	 * 3. 상품 삭제
	 * 4. 좋아요 수 증가
	 * 5. 좋아요 수 감소
	 */

	// 1. 상품 생성
	@Transactional
	public Product createProduct(AdminProductCreateInDto inDto) {

		// 상품 생성
		Product product = Product.create(
			inDto.brandId(),
			inDto.name(),
			inDto.price(),
			inDto.stock(),
			inDto.description()
		);

		// 저장
		return productCommandRepository.save(product);
	}


	// 2. 상품 수정
	@Transactional
	public Product updateProduct(Product product, AdminProductUpdateInDto inDto) {

		// 상품 정보 변경
		product.changeName(inDto.name());
		product.changePrice(inDto.price());
		product.changeStock(inDto.stock());
		product.changeDescription(inDto.description());

		// 저장
		return productCommandRepository.save(product);
	}


	// 3. 상품 삭제
	@Transactional
	public void deleteProduct(Product product) {

		// 삭제 (도메인 로직)
		product.delete();

		// 삭제 저장
		productCommandRepository.delete(product);

		// 삭제 이벤트 발행
		eventPublisher.publishEvent(new ProductDeletedEvent(product.getId()));
	}


	// 4. 좋아요 수 증가
	@Transactional
	public void increaseLikeCount(Product product) {

		// 좋아요 수 증가
		product.increaseLikeCount();

		// 저장
		productCommandRepository.save(product);
	}


	// 5. 좋아요 수 감소
	@Transactional
	public void decreaseLikeCount(Product product) {

		// 좋아요 수 감소
		product.decreaseLikeCount();

		// 저장
		productCommandRepository.save(product);
	}

}
