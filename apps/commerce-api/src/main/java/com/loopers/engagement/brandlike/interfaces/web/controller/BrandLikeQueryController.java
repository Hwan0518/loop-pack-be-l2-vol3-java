package com.loopers.engagement.brandlike.interfaces.web.controller;

import com.loopers.engagement.brandlike.application.dto.out.BrandLikePageOutDto;
import com.loopers.engagement.brandlike.application.facade.BrandLikeQueryFacade;
import com.loopers.engagement.brandlike.interfaces.web.response.BrandLikeCheckResponse;
import com.loopers.engagement.brandlike.interfaces.web.response.BrandLikePageResponse;
import com.loopers.support.common.auth.AuthenticationResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/users/me/brand-likes")
@RequiredArgsConstructor
public class BrandLikeQueryController {

	// facade
	private final BrandLikeQueryFacade brandLikeQueryFacade;
	// auth
	private final AuthenticationResolver authenticationResolver;


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

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// 브랜드 좋아요 목록 조회
		BrandLikePageOutDto outDto = brandLikeQueryFacade.getLikesByUserId(userId, page, size);

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

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// 좋아요 여부 확인
		boolean liked = brandLikeQueryFacade.isLikedByUser(userId, targetId);

		// 200 OK 반환
		return ResponseEntity.ok(new BrandLikeCheckResponse(liked));
	}

}
