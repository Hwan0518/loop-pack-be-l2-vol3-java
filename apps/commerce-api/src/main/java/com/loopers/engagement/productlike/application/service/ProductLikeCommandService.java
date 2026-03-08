package com.loopers.engagement.productlike.application.service;

import com.loopers.engagement.productlike.application.port.out.client.catalog.ProductLikeCountSyncer;
import com.loopers.engagement.productlike.application.port.out.client.catalog.ProductLikeTargetValidator;
import com.loopers.engagement.productlike.domain.model.ProductLike;
import com.loopers.engagement.productlike.domain.repository.ProductLikeCommandRepository;
import com.loopers.engagement.productlike.domain.repository.ProductLikeQueryRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


@Service
@RequiredArgsConstructor
public class ProductLikeCommandService {

	// repository
	private final ProductLikeCommandRepository productLikeCommandRepository;
	private final ProductLikeQueryRepository productLikeQueryRepository;
	// port
	private final ProductLikeTargetValidator productLikeTargetValidator;
	private final ProductLikeCountSyncer productLikeCountSyncer;


	/**
	 * 상품 좋아요 명령 서비스
	 * 1. 좋아요 조회
	 * 2. 상품 좋아요 생성
	 * 3. 상품 좋아요 삭제
	 * 4. 상품 ID로 상품 좋아요 전체 삭제
	 * 5. 좋아요 수 증가
	 * 6. 좋아요 수 감소
	 */

	// 1. 좋아요 조회
	@Transactional(readOnly = true)
	public Optional<ProductLike> findLike(Long userId, Long targetId) {
		return productLikeQueryRepository.findByUserIdAndTargetId(userId, targetId);
	}


	// 2. 상품 좋아요 생성
	@Transactional
	public ProductLike createLike(Long userId, Long targetId) {

		// 좋아요 대상 상품 존재 여부 검증 (Provider 예외 → Consumer 에러 매핑)
		try {
			productLikeTargetValidator.validate(targetId);
		} catch (CoreException e) {
			throw new CoreException(ErrorType.LIKE_TARGET_NOT_FOUND);
		}

		// 좋아요 생성 및 저장
		ProductLike productLike = ProductLike.create(userId, targetId);
		return productLikeCommandRepository.save(productLike);
	}


	// 3. 상품 좋아요 삭제
	@Transactional
	public void deleteLike(Long userId, Long targetId) {

		// 좋아요 조회
		ProductLike productLike = productLikeQueryRepository
			.findByUserIdAndTargetId(userId, targetId)
			.orElseThrow(() -> new CoreException(ErrorType.LIKE_NOT_FOUND));

		// 삭제
		productLikeCommandRepository.delete(productLike);
	}


	// 4. 상품 ID로 상품 좋아요 전체 삭제
	@Transactional
	public void deleteAllByTargetId(Long targetId) {
		productLikeCommandRepository.deleteAllByTargetId(targetId);
	}


	// 5. 좋아요 수 증가
	@Transactional
	public void increaseLikeCount(Long productId) {
		productLikeCountSyncer.increaseLikeCount(productId);
	}


	// 6. 좋아요 수 감소
	@Transactional
	public void decreaseLikeCount(Long productId) {
		productLikeCountSyncer.decreaseLikeCount(productId);
	}

}
