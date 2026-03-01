package com.loopers.engagement.brandlike.application.service;

import com.loopers.engagement.brandlike.application.dto.out.BrandLikePageOutDto;
import com.loopers.engagement.brandlike.domain.model.BrandLike;
import com.loopers.engagement.brandlike.domain.repository.BrandLikeQueryRepository;
import com.loopers.engagement.brandlike.domain.repository.vo.PageCriteria;
import com.loopers.engagement.brandlike.domain.repository.vo.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class BrandLikeQueryService {

	// repository
	private final BrandLikeQueryRepository brandLikeQueryRepository;


	/**
	 * 브랜드 좋아요 조회 서비스
	 * 1. 사용자의 브랜드 좋아요 목록 조회
	 * 2. 사용자의 브랜드 좋아요 여부 확인
	 */

	// 1. 사용자의 브랜드 좋아요 목록 조회
	@Transactional(readOnly = true)
	public BrandLikePageOutDto getLikesByUserId(Long userId, int page, int size) {

		// 페이지네이션 조회
		PageResult<BrandLike> pageResult = brandLikeQueryRepository
			.findByUserId(userId, new PageCriteria(page, size));

		// DTO 변환
		return BrandLikePageOutDto.from(pageResult);
	}


	// 2. 사용자의 브랜드 좋아요 여부 확인
	@Transactional(readOnly = true)
	public boolean isLikedByUser(Long userId, Long targetId) {
		return brandLikeQueryRepository.existsByUserIdAndTargetId(userId, targetId);
	}

}
