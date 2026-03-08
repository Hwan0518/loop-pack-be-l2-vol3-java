package com.loopers.engagement.brandlike.application.facade;

import com.loopers.engagement.brandlike.application.dto.out.BrandLikeOutDto;
import com.loopers.engagement.brandlike.application.service.BrandLikeCommandService;
import com.loopers.engagement.brandlike.domain.model.BrandLike;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


@Service
@RequiredArgsConstructor
public class BrandLikeCommandFacade {

	// service
	private final BrandLikeCommandService brandLikeCommandService;


	/**
	 * 브랜드 좋아요 명령 퍼사드
	 * 1. 브랜드 좋아요 생성 (멱등 — DB 유니크 제약으로 중복 방지)
	 * 2. 브랜드 좋아요 삭제
	 * 3. 브랜드 ID로 브랜드 좋아요 전체 삭제 (Cross-BC 전용 — ACL에서 호출)
	 */

	// 1. 브랜드 좋아요 생성 (멱등 — 사전 조회로 99.9% 중복 차단, DB 유니크 제약이 데이터 무결성 보장)
	@Transactional
	public BrandLikeOutDto createLike(Long userId, Long targetId) {

		// 기존 좋아요 존재 시 기존 반환 (멱등 — 따닥 등 대부분의 중복 요청을 여기서 차단)
		Optional<BrandLike> existing = brandLikeCommandService.findLike(userId, targetId);
		if (existing.isPresent()) {
			return BrandLikeOutDto.from(existing.get());
		}

		// 좋아요 생성 (0.01% 레이스 시 DB 유니크 제약이 안전망 역할 — 500 → 클라이언트 재시도 → 사전 조회에서 멱등 반환)
		BrandLike brandLike = brandLikeCommandService.createLike(userId, targetId);
		return BrandLikeOutDto.from(brandLike);
	}


	// 2. 브랜드 좋아요 삭제
	@Transactional
	public void deleteLike(Long userId, Long targetId) {

		// 좋아요 삭제
		brandLikeCommandService.deleteLike(userId, targetId);
	}


	// 3. 브랜드 ID로 브랜드 좋아요 전체 삭제 (Cross-BC 전용 — ACL에서 호출)
	@Transactional
	public void deleteAllByBrandId(Long brandId) {
		brandLikeCommandService.deleteAllByTargetId(brandId);
	}

}
