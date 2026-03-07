package com.loopers.engagement.brandlike.interfaces.web.controller;

import com.loopers.engagement.brandlike.application.dto.out.BrandLikeOutDto;
import com.loopers.engagement.brandlike.application.facade.BrandLikeCommandFacade;
import com.loopers.engagement.brandlike.interfaces.web.response.BrandLikeResponse;
import com.loopers.support.common.auth.AuthenticationResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
public class BrandLikeCommandController {

	// facade
	private final BrandLikeCommandFacade brandLikeCommandFacade;
	// auth
	private final AuthenticationResolver authenticationResolver;


	/**
	 * 브랜드 좋아요 명령 컨트롤러 (사용자 인증 필요)
	 * 1. 브랜드 좋아요 생성
	 * 2. 브랜드 좋아요 삭제
	 */

	// 1. 브랜드 좋아요 생성
	@PostMapping("/api/v1/brands/{id}/likes")
	public ResponseEntity<BrandLikeResponse> createBrandLike(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@PathVariable Long id
	) {

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// 좋아요 생성
		BrandLikeOutDto outDto = brandLikeCommandFacade.createLike(userId, id);

		// 응답 변환
		BrandLikeResponse response = BrandLikeResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}


	// 2. 브랜드 좋아요 삭제
	@DeleteMapping("/api/v1/brands/{id}/likes")
	public ResponseEntity<Void> deleteBrandLike(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@PathVariable Long id
	) {

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// 좋아요 삭제
		brandLikeCommandFacade.deleteLike(userId, id);

		// 200 OK 반환
		return ResponseEntity.ok().build();
	}

}
