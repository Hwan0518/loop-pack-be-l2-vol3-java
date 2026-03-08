package com.loopers.engagement.productlike.interfaces.web.controller;

import com.loopers.engagement.productlike.application.dto.out.ProductLikePageOutDto;
import com.loopers.engagement.productlike.application.facade.ProductLikeQueryFacade;
import com.loopers.engagement.productlike.interfaces.web.response.ProductLikeCheckResponse;
import com.loopers.engagement.productlike.interfaces.web.response.ProductLikePageResponse;
import com.loopers.support.common.auth.AuthenticationResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/users/me/product-likes")
@RequiredArgsConstructor
public class ProductLikeQueryController {

	// facade
	private final ProductLikeQueryFacade productLikeQueryFacade;
	// auth
	private final AuthenticationResolver authenticationResolver;


	/**
	 * 상품 좋아요 조회 컨트롤러 (사용자 인증 필요)
	 * 1. 내 상품 좋아요 목록 조회
	 * 2. 상품 좋아요 여부 확인
	 */

	// 1. 내 상품 좋아요 목록 조회
	@GetMapping
	public ResponseEntity<ProductLikePageResponse> getMyProductLikes(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) {

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// 상품 좋아요 목록 조회
		ProductLikePageOutDto outDto = productLikeQueryFacade.getLikesByUserId(userId, page, size);

		// 응답 변환
		ProductLikePageResponse response = ProductLikePageResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}


	// 2. 상품 좋아요 여부 확인
	@GetMapping("/check")
	public ResponseEntity<ProductLikeCheckResponse> checkProductLike(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@RequestParam Long targetId
	) {

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// 좋아요 여부 확인
		boolean liked = productLikeQueryFacade.isLikedByUser(userId, targetId);

		// 200 OK 반환
		return ResponseEntity.ok(new ProductLikeCheckResponse(liked));
	}

}
