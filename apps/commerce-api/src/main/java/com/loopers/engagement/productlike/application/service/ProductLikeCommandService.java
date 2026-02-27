package com.loopers.engagement.productlike.application.service;

import com.loopers.engagement.productlike.application.port.out.client.catalog.ProductLikeTargetValidator;
import com.loopers.engagement.productlike.application.port.out.client.user.UserAuthenticator;
import com.loopers.engagement.productlike.domain.event.ProductLikeCancelledEvent;
import com.loopers.engagement.productlike.domain.event.ProductLikeCreatedEvent;
import com.loopers.engagement.productlike.domain.model.ProductLike;
import com.loopers.engagement.productlike.domain.repository.ProductLikeCommandRepository;
import com.loopers.engagement.productlike.domain.repository.ProductLikeQueryRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
	private final UserAuthenticator userAuthenticator;
	// event
	private final ApplicationEventPublisher eventPublisher;


	/**
	 * 상품 좋아요 명령 서비스
	 * 1. 사용자 인증
	 * 2. 상품 좋아요 생성 (멱등)
	 * 3. 상품 좋아요 삭제
	 * 4. 상품 ID로 상품 좋아요 전체 삭제
	 */

	// 1. 사용자 인증
	@Transactional(readOnly = true)
	public Long authenticate(String loginId, String password) {
		return userAuthenticator.authenticate(loginId, password);
	}

	// 2. 상품 좋아요 생성 (멱등)
	@Transactional
	public ProductLike createLike(Long userId, Long targetId) {

		// 좋아요 대상 상품 존재 여부 검증 (Provider 예외 → Consumer 에러 매핑)
		try {
			productLikeTargetValidator.validate(targetId);
		} catch (CoreException e) {
			throw new CoreException(ErrorType.LIKE_TARGET_NOT_FOUND);
		}

		// 기존 좋아요 존재 시 기존 좋아요 반환 (멱등)
		Optional<ProductLike> existing = productLikeQueryRepository
			.findByUserIdAndTargetId(userId, targetId);
		if (existing.isPresent()) {
			return existing.get();
		}

		// 좋아요 생성 및 저장
		ProductLike productLike = ProductLike.create(userId, targetId);
		ProductLike savedLike = productLikeCommandRepository.save(productLike);

		// 이벤트 발행
		eventPublisher.publishEvent(new ProductLikeCreatedEvent(targetId));

		return savedLike;
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

		// 이벤트 발행
		eventPublisher.publishEvent(new ProductLikeCancelledEvent(targetId));
	}


	// 4. 상품 ID로 상품 좋아요 전체 삭제
	@Transactional
	public void deleteAllByTargetId(Long targetId) {
		productLikeCommandRepository.deleteAllByTargetId(targetId);
	}

}
