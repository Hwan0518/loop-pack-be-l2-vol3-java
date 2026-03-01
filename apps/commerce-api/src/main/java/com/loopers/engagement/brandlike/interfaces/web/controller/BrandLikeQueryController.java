package com.loopers.engagement.brandlike.interfaces.web.controller;

import com.loopers.engagement.brandlike.application.dto.out.BrandLikePageOutDto;
import com.loopers.engagement.brandlike.application.facade.BrandLikeQueryFacade;
import com.loopers.engagement.brandlike.interfaces.web.response.BrandLikeCheckResponse;
import com.loopers.engagement.brandlike.interfaces.web.response.BrandLikePageResponse;
import com.loopers.user.user.support.common.HeaderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/users/me/brand-likes")
@RequiredArgsConstructor
public class BrandLikeQueryController {

	// facade
	private final BrandLikeQueryFacade brandLikeQueryFacade;


	/**
	 * 브랜드 좋아요 조회 컨트롤러 (사용자 인증 필요)
	 * 1. 내 브랜드 좋아요 목록 조회
	 * 2. 브랜드 좋아요 여부 확인
	 */

	// 1. 내 브랜드 좋아요 목록 조회
	@GetMapping
	public ResponseEntity<BrandLikePageResponse> getMyBrandLikes(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) {

		// 인증 헤더 검증
		HeaderValidator.validate(loginId, password);

		// 브랜드 좋아요 목록 조회
		BrandLikePageOutDto outDto = brandLikeQueryFacade.getLikesByUserId(loginId, password, page, size);

		// 응답 변환
		BrandLikePageResponse response = BrandLikePageResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}


	// 2. 브랜드 좋아요 여부 확인
	@GetMapping("/check")
	public ResponseEntity<BrandLikeCheckResponse> checkBrandLike(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@RequestParam Long targetId
	) {

		// 인증 헤더 검증
		HeaderValidator.validate(loginId, password);

		// 좋아요 여부 확인
		boolean liked = brandLikeQueryFacade.isLikedByUser(loginId, password, targetId);

		// 200 OK 반환
		return ResponseEntity.ok(new BrandLikeCheckResponse(liked));
	}

}
