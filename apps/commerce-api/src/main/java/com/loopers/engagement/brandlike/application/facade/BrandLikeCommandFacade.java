package com.loopers.engagement.brandlike.application.facade;

import com.loopers.engagement.brandlike.application.dto.out.BrandLikeOutDto;
import com.loopers.engagement.brandlike.application.service.BrandLikeCommandService;
import com.loopers.engagement.brandlike.domain.model.BrandLike;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class BrandLikeCommandFacade {

	// service
	private final BrandLikeCommandService brandLikeCommandService;


	/**
	 * 브랜드 좋아요 명령 퍼사드
	 * 1. 브랜드 좋아요 생성
	 * 2. 브랜드 좋아요 삭제
	 */

	// 1. 브랜드 좋아요 생성
	@Transactional
	public BrandLikeOutDto createLike(String loginId, String password, Long targetId) {

		// 사용자 인증
		Long userId = brandLikeCommandService.authenticate(loginId, password);

		// 좋아요 생성
		BrandLike brandLike = brandLikeCommandService.createLike(userId, targetId);

		// DTO 변환
		return BrandLikeOutDto.from(brandLike);
	}


	// 2. 브랜드 좋아요 삭제
	@Transactional
	public void deleteLike(String loginId, String password, Long targetId) {

		// 사용자 인증
		Long userId = brandLikeCommandService.authenticate(loginId, password);

		// 좋아요 삭제
		brandLikeCommandService.deleteLike(userId, targetId);
	}

}
