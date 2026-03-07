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

		// 좋아요 생성 (0.01% 레이스 시 DB 유니크 제약이 안전망 역할 — 500 → 클라이언트 재시도 → 사전 조회에서 멱등 반환)
		ProductLike productLike = productLikeCommandService.createLike(userId, targetId);

		// 좋아요 수 증가 (Cross-BC 부수효과)
		productLikeCommandService.increaseLikeCount(targetId);

		// 반환
		return ProductLikeOutDto.from(productLike);
	}


	// 2. 상품 좋아요 삭제
	@Transactional
	public void deleteLike(Long userId, Long targetId) {

		// 좋아요 삭제
		productLikeCommandService.deleteLike(userId, targetId);

		// 좋아요 수 감소 (Cross-BC 부수효과)
		productLikeCommandService.decreaseLikeCount(targetId);
	}


	// 3. 상품 ID로 상품 좋아요 전체 삭제 (Cross-BC 전용 — ACL에서 호출)
	@Transactional
	public void deleteAllByProductId(Long productId) {
		productLikeCommandService.deleteAllByTargetId(productId);
	}

}
