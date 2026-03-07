package com.loopers.engagement.productlike.interfaces.web.controller;

import com.loopers.engagement.productlike.application.dto.out.ProductLikeOutDto;
import com.loopers.engagement.productlike.application.facade.ProductLikeCommandFacade;
import com.loopers.engagement.productlike.interfaces.web.response.ProductLikeResponse;
import com.loopers.support.common.auth.AuthenticationResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
public class ProductLikeCommandController {

	// facade
	private final ProductLikeCommandFacade productLikeCommandFacade;
	// auth
	private final AuthenticationResolver authenticationResolver;


	/**
	 * 상품 좋아요 명령 컨트롤러 (사용자 인증 필요)
	 * 1. 상품 좋아요 생성
	 * 2. 상품 좋아요 삭제
	 */

	// 1. 상품 좋아요 생성
	@PostMapping("/api/v1/products/{id}/likes")
	public ResponseEntity<ProductLikeResponse> createProductLike(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@PathVariable Long id
	) {

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// 좋아요 생성
		ProductLikeOutDto outDto = productLikeCommandFacade.createLike(userId, id);

		// 응답 변환
		ProductLikeResponse response = ProductLikeResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}


	// 2. 상품 좋아요 삭제
	@DeleteMapping("/api/v1/products/{id}/likes")
	public ResponseEntity<Void> deleteProductLike(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@PathVariable Long id
	) {

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// 좋아요 삭제
		productLikeCommandFacade.deleteLike(userId, id);

		// 200 OK 반환
		return ResponseEntity.ok().build();
	}

}
