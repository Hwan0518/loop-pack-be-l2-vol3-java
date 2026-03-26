package com.loopers.coupon.coupontemplate.application.facade;


import com.loopers.coupon.coupontemplate.application.dto.in.AdminCreateCouponTemplateInDto;
import com.loopers.coupon.coupontemplate.application.dto.in.AdminUpdateCouponTemplateInDto;
import com.loopers.coupon.coupontemplate.application.dto.out.AdminCouponTemplateOutDto;
import com.loopers.coupon.coupontemplate.application.service.CouponTemplateCommandService;
import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class CouponTemplateCommandFacade {

	// service
	private final CouponTemplateCommandService couponTemplateCommandService;


	/**
	 * 쿠폰 템플릿 명령 파사드
	 * 1. 쿠폰 템플릿 생성
	 * 2. 쿠폰 템플릿 수정
	 * 3. 쿠폰 템플릿 삭제
	 */

	// 1. 쿠폰 템플릿 생성
	@Transactional
	public AdminCouponTemplateOutDto createCouponTemplate(AdminCreateCouponTemplateInDto inDto) {

		// 도메인 모델 생성 (검증 포함)
		CouponTemplate template = CouponTemplate.create(
			inDto.name(),
			inDto.type(),
			inDto.value(),
			inDto.minOrderAmount(),
			inDto.maxQuantity(),
			inDto.expiredAt()
		);

		// 저장
		CouponTemplate saved = couponTemplateCommandService.save(template);

		// 결과 반환
		return AdminCouponTemplateOutDto.from(saved);
	}


	// 2. 쿠폰 템플릿 수정
	@Transactional
	public AdminCouponTemplateOutDto updateCouponTemplate(Long id, AdminUpdateCouponTemplateInDto inDto) {

		// 수정
		CouponTemplate updated = couponTemplateCommandService.update(
			id,
			inDto.name(),
			inDto.expiredAt()
		);

		// 결과 반환
		return AdminCouponTemplateOutDto.from(updated);
	}


	// 3. 쿠폰 템플릿 삭제 (soft delete)
	@Transactional
	public void deleteCouponTemplate(Long id) {
		couponTemplateCommandService.delete(id);
	}

}
