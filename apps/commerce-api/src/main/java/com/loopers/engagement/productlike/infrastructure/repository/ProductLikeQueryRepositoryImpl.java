package com.loopers.engagement.productlike.infrastructure.repository;

import com.loopers.engagement.productlike.domain.model.ProductLike;
import com.loopers.engagement.productlike.domain.repository.ProductLikeQueryRepository;
import com.loopers.engagement.productlike.domain.repository.vo.PageCriteria;
import com.loopers.engagement.productlike.domain.repository.vo.PageResult;
import com.loopers.engagement.productlike.infrastructure.entity.ProductLikeEntity;
import com.loopers.engagement.productlike.infrastructure.jpa.ProductLikeJpaRepository;
import com.loopers.engagement.productlike.infrastructure.mapper.ProductLikeEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
@RequiredArgsConstructor
public class ProductLikeQueryRepositoryImpl implements ProductLikeQueryRepository {

	// jpa
	private final ProductLikeJpaRepository productLikeJpaRepository;
	// mapper
	private final ProductLikeEntityMapper productLikeMapper;

	private static final String TARGET_TYPE = "PRODUCT";


	/**
	 * 상품 좋아요 조회 리포지토리 구현체
	 * 1. 사용자 ID와 상품 ID로 좋아요 조회
	 * 2. 사용자 ID와 상품 ID로 좋아요 존재 여부 확인
	 * 3. 사용자 ID로 상품 좋아요 목록 조회 (페이지네이션)
	 */

	// 1. 사용자 ID와 상품 ID로 좋아요 조회
	@Override
	public Optional<ProductLike> findByUserIdAndTargetId(Long userId, Long targetId) {
		return productLikeJpaRepository
			.findByUserIdAndTargetTypeAndTargetId(userId, TARGET_TYPE, targetId)
			.map(productLikeMapper::toDomain);
	}


	// 2. 사용자 ID와 상품 ID로 좋아요 존재 여부 확인
	@Override
	public boolean existsByUserIdAndTargetId(Long userId, Long targetId) {
		return productLikeJpaRepository
			.existsByUserIdAndTargetTypeAndTargetId(userId, TARGET_TYPE, targetId);
	}


	// 3. 사용자 ID로 상품 좋아요 목록 조회 (페이지네이션)
	@Override
	public PageResult<ProductLike> findByUserId(Long userId, PageCriteria pageCriteria) {

		// Spring Page 조회 (최신순 정렬)
		PageRequest pageRequest = PageRequest.of(
			pageCriteria.page(), pageCriteria.size(), Sort.by(Sort.Direction.DESC, "id"));
		Page<ProductLikeEntity> page = productLikeJpaRepository
			.findByUserIdAndTargetType(userId, TARGET_TYPE, pageRequest);

		// 도메인 PageResult 변환
		return new PageResult<>(
			page.getContent().stream().map(productLikeMapper::toDomain).toList(),
			pageCriteria.page(),
			pageCriteria.size(),
			page.getTotalElements()
		);
	}

}
