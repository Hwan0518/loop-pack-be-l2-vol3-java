package com.loopers.coupon.coupontemplate.interfaces.web.controller;


import com.loopers.coupon.coupontemplate.application.dto.in.AdminCreateCouponTemplateInDto;
import com.loopers.coupon.coupontemplate.application.dto.in.AdminUpdateCouponTemplateInDto;
import com.loopers.coupon.coupontemplate.application.dto.out.AdminCouponTemplateOutDto;
import com.loopers.coupon.coupontemplate.application.facade.CouponTemplateCommandFacade;
import com.loopers.coupon.coupontemplate.interfaces.web.request.AdminCreateCouponTemplateRequest;
import com.loopers.coupon.coupontemplate.interfaces.web.request.AdminUpdateCouponTemplateRequest;
import com.loopers.coupon.coupontemplate.interfaces.web.response.AdminCouponTemplateResponse;
import com.loopers.support.common.AdminHeaderValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api-admin/v1/coupons")
@RequiredArgsConstructor
public class CouponTemplateAdminCommandController {

	// facade
	private final CouponTemplateCommandFacade couponTemplateCommandFacade;


	/**
	 * 쿠폰 템플릿 관리자 명령 컨트롤러 (LDAP 인증 필요)
	 * 1. 쿠폰 템플릿 생성
	 * 2. 쿠폰 템플릿 수정
	 * 3. 쿠폰 템플릿 삭제
	 */

	// 1. 쿠폰 템플릿 생성
	@PostMapping
	public ResponseEntity<AdminCouponTemplateResponse> createCouponTemplate(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@Valid @RequestBody AdminCreateCouponTemplateRequest request
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// InDto 생성
		AdminCreateCouponTemplateInDto inDto = new AdminCreateCouponTemplateInDto(
			request.name(), request.type(), request.value(),
			request.minOrderAmount(), request.expiredAt()
		);

		// 쿠폰 템플릿 생성
		AdminCouponTemplateOutDto outDto = couponTemplateCommandFacade.createCouponTemplate(inDto);

		// 응답 변환
		AdminCouponTemplateResponse response = AdminCouponTemplateResponse.from(outDto);

		// 201 Created 반환
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}


	// 2. 쿠폰 템플릿 수정
	@PutMapping("/{couponId}")
	public ResponseEntity<AdminCouponTemplateResponse> updateCouponTemplate(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@PathVariable Long couponId,
		@RequestBody AdminUpdateCouponTemplateRequest request
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// InDto 생성
		AdminUpdateCouponTemplateInDto inDto = new AdminUpdateCouponTemplateInDto(
			request.name(), request.expiredAt()
		);

		// 쿠폰 템플릿 수정
		AdminCouponTemplateOutDto outDto = couponTemplateCommandFacade.updateCouponTemplate(couponId, inDto);

		// 응답 변환
		AdminCouponTemplateResponse response = AdminCouponTemplateResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}


	// 3. 쿠폰 템플릿 삭제
	@DeleteMapping("/{couponId}")
	public ResponseEntity<Void> deleteCouponTemplate(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@PathVariable Long couponId
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// 쿠폰 템플릿 삭제
		couponTemplateCommandFacade.deleteCouponTemplate(couponId);

		// 204 No Content 반환
		return ResponseEntity.noContent().build();
	}

}
