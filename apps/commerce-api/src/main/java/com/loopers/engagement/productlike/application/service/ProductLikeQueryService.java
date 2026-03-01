package com.loopers.engagement.productlike.application.service;

import com.loopers.engagement.productlike.application.dto.out.ProductLikePageOutDto;
import com.loopers.engagement.productlike.domain.model.ProductLike;
import com.loopers.engagement.productlike.domain.repository.ProductLikeQueryRepository;
import com.loopers.engagement.productlike.domain.repository.vo.PageCriteria;
import com.loopers.engagement.productlike.domain.repository.vo.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class ProductLikeQueryService {

	// repository
	private final ProductLikeQueryRepository productLikeQueryRepository;


	/**
	 * 상품 좋아요 조회 서비스
	 * 1. 사용자의 상품 좋아요 목록 조회
	 * 2. 사용자의 상품 좋아요 여부 확인
	 */

	// 1. 사용자의 상품 좋아요 목록 조회
	@Transactional(readOnly = true)
	public ProductLikePageOutDto getLikesByUserId(Long userId, int page, int size) {

		// 페이지네이션 조회
		PageResult<ProductLike> pageResult = productLikeQueryRepository
			.findByUserId(userId, new PageCriteria(page, size));

		// DTO 변환
		return ProductLikePageOutDto.from(pageResult);
	}


	// 2. 사용자의 상품 좋아요 여부 확인
	@Transactional(readOnly = true)
	public boolean isLikedByUser(Long userId, Long targetId) {
		return productLikeQueryRepository.existsByUserIdAndTargetId(userId, targetId);
	}

}
