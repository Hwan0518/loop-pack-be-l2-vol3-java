package com.loopers.engagement.brandlike.application.facade;

import com.loopers.engagement.brandlike.application.dto.out.BrandLikePageOutDto;
import com.loopers.engagement.brandlike.application.service.BrandLikeCommandService;
import com.loopers.engagement.brandlike.application.service.BrandLikeQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class BrandLikeQueryFacade {

	// service
	private final BrandLikeCommandService brandLikeCommandService;
	private final BrandLikeQueryService brandLikeQueryService;


	/**
	 * 브랜드 좋아요 조회 퍼사드
	 * 1. 사용자의 브랜드 좋아요 목록 조회
	 * 2. 사용자의 브랜드 좋아요 여부 확인
	 */

	// 1. 사용자의 브랜드 좋아요 목록 조회
	@Transactional(readOnly = true)
	public BrandLikePageOutDto getLikesByUserId(String loginId, String password, int page, int size) {

		// 사용자 인증
		Long userId = brandLikeCommandService.authenticate(loginId, password);

		return brandLikeQueryService.getLikesByUserId(userId, page, size);
	}


	// 2. 사용자의 브랜드 좋아요 여부 확인
	@Transactional(readOnly = true)
	public boolean isLikedByUser(String loginId, String password, Long targetId) {

		// 사용자 인증
		Long userId = brandLikeCommandService.authenticate(loginId, password);

		return brandLikeQueryService.isLikedByUser(userId, targetId);
	}

}
