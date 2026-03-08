package com.loopers.coupon.coupontemplate.application.service;


import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.coupontemplate.domain.repository.CouponTemplateCommandRepository;
import com.loopers.coupon.coupontemplate.domain.repository.CouponTemplateQueryRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Service
@RequiredArgsConstructor
public class CouponTemplateCommandService {

	// repository
	private final CouponTemplateCommandRepository couponTemplateCommandRepository;
	private final CouponTemplateQueryRepository couponTemplateQueryRepository;


	/**
	 * 쿠폰 템플릿 명령 서비스
	 * 1. 쿠폰 템플릿 저장
	 * 2. 쿠폰 템플릿 수정
	 * 3. 쿠폰 템플릿 삭제 (soft delete)
	 */

	// 1. 쿠폰 템플릿 저장
	@Transactional
	public CouponTemplate save(CouponTemplate couponTemplate) {
		return couponTemplateCommandRepository.save(couponTemplate);
	}


	// 2. 쿠폰 템플릿 수정 (name, expiredAt)
	@Transactional
	public CouponTemplate update(Long id, String name, LocalDateTime expiredAt) {

		// 쿠폰 템플릿 조회 (없으면 예외)
		CouponTemplate template = couponTemplateQueryRepository.findById(id)
			.orElseThrow(() -> new CoreException(ErrorType.COUPON_TEMPLATE_NOT_FOUND));

		// 삭제된 템플릿은 수정 불가
		if (template.isDeleted()) {
			throw new CoreException(ErrorType.COUPON_TEMPLATE_NOT_FOUND);
		}

		// 필드 변경
		if (name != null) {
			template.changeName(name);
		}
		if (expiredAt != null) {
			template.changeExpiredAt(expiredAt);
		}

		// 저장
		return couponTemplateCommandRepository.save(template);
	}


	// 3. 쿠폰 템플릿 삭제 (soft delete)
	@Transactional
	public void delete(Long id) {

		// 쿠폰 템플릿 조회 (없으면 예외)
		CouponTemplate template = couponTemplateQueryRepository.findById(id)
			.orElseThrow(() -> new CoreException(ErrorType.COUPON_TEMPLATE_NOT_FOUND));

		// 이미 삭제된 경우 예외
		if (template.isDeleted()) {
			throw new CoreException(ErrorType.COUPON_TEMPLATE_NOT_FOUND);
		}

		// soft delete 처리 후 저장
		template.delete();
		couponTemplateCommandRepository.save(template);
	}

}
