package com.loopers.engagement.productlike.application.facade;


import com.loopers.engagement.productlike.application.dto.out.ProductLikeOutDto;
import com.loopers.engagement.productlike.application.service.ProductLikeCommandService;
import com.loopers.engagement.productlike.domain.model.ProductLike;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


@Service
@RequiredArgsConstructor
public class ProductLikeCommandFacade {

	// service
	private final ProductLikeCommandService productLikeCommandService;


	/**
	 * 상품 좋아요 명령 퍼사드
	 * 1. 상품 좋아요 생성 (멱등 — DB 유니크 제약으로 중복 방지)
	 * 2. 상품 좋아요 삭제
	 * 3. 상품 ID로 상품 좋아요 전체 삭제 (Cross-BC 전용 — ACL에서 호출)
	 */

	// 1. 상품 좋아요 생성 (멱등 — 사전 조회로 99.9% 중복 차단, DB 유니크 제약이 데이터 무결성 보장)
	@Transactional
	public ProductLikeOutDto createLike(Long userId, Long targetId) {

		// 기존 좋아요 존재 시 기존 반환 (멱등 — 따닥 등 대부분의 중복 요청을 여기서 차단)
		Optional<ProductLike> existing = productLikeCommandService.findLike(userId, targetId);
		if (existing.isPresent()) {
			return ProductLikeOutDto.from(existing.get());
		}

		// 좋아요 생성 + Outbox 저장 (Service 내부에서 처리)
		ProductLike productLike = productLikeCommandService.createLike(userId, targetId);

		return ProductLikeOutDto.from(productLike);
	}


	// 2. 상품 좋아요 삭제
	@Transactional
	public void deleteLike(Long userId, Long targetId) {

		// 좋아요 삭제 + Outbox 저장 (Service 내부에서 처리)
		productLikeCommandService.deleteLike(userId, targetId);
	}


	// 3. 상품 ID로 상품 좋아요 전체 삭제 (Cross-BC 전용 — ACL에서 호출)
	@Transactional
	public void deleteAllByProductId(Long productId) {
		productLikeCommandService.deleteAllByTargetId(productId);
	}

}
