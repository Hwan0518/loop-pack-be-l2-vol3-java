package com.loopers.engagement.productlike.application.service;


import com.loopers.support.common.event.catalog.ProductLikedPayload;
import com.loopers.support.common.event.catalog.ProductUnlikedPayload;
import com.loopers.engagement.productlike.application.port.out.client.catalog.ProductLikeTargetValidator;
import com.loopers.engagement.productlike.domain.model.ProductLike;
import com.loopers.engagement.productlike.domain.repository.ProductLikeCommandRepository;
import com.loopers.engagement.productlike.domain.repository.ProductLikeQueryRepository;
import com.loopers.support.common.event.EventType;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.support.common.outbox.application.port.OutboxEventPort;
import com.loopers.support.common.outbox.application.util.JsonSerializer;
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
	// outbox
	private final OutboxEventPort outboxEventPort;
	private final JsonSerializer jsonSerializer;


	/**
	 * 상품 좋아요 명령 서비스
	 * 1. 좋아요 조회
	 * 2. 상품 좋아요 생성 (+ Outbox 저장)
	 * 3. 상품 좋아요 삭제 (+ Outbox 저장)
	 * 4. 상품 ID로 상품 좋아요 전체 삭제
	 */

	// 1. 좋아요 조회
	@Transactional(readOnly = true)
	public Optional<ProductLike> findLike(Long userId, Long targetId) {
		return productLikeQueryRepository.findByUserIdAndTargetId(userId, targetId);
	}


	// 2. 상품 좋아요 생성 (+ Outbox 저장)
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
		ProductLike saved = productLikeCommandRepository.save(productLike);

		// Outbox 저장 (같은 TX — At Least Once 보장)
		outboxEventPort.save(EventType.PRODUCT_LIKED, String.valueOf(targetId),
			String.valueOf(targetId),
			jsonSerializer.toJson(ProductLikedPayload.of(saved.getId(), saved.getUserId(), saved.getTargetId())));

		return saved;
	}


	// 3. 상품 좋아요 삭제 (+ Outbox 저장)
	@Transactional
	public void deleteLike(Long userId, Long targetId) {

		// 좋아요 조회
		ProductLike productLike = productLikeQueryRepository
			.findByUserIdAndTargetId(userId, targetId)
			.orElseThrow(() -> new CoreException(ErrorType.LIKE_NOT_FOUND));

		// 삭제
		productLikeCommandRepository.delete(productLike);

		// Outbox 저장 (같은 TX — At Least Once 보장)
		outboxEventPort.save(EventType.PRODUCT_UNLIKED, String.valueOf(targetId),
			String.valueOf(targetId),
			jsonSerializer.toJson(ProductUnlikedPayload.of(userId, targetId)));
	}


	// 4. 상품 ID로 상품 좋아요 전체 삭제
	@Transactional
	public void deleteAllByTargetId(Long targetId) {
		productLikeCommandRepository.deleteAllByTargetId(targetId);
	}

}
