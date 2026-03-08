package com.loopers.coupon.coupontemplate.application.service;


import com.loopers.coupon.coupontemplate.application.dto.out.AdminCouponTemplateOutDto;
import com.loopers.coupon.coupontemplate.application.dto.out.AdminCouponTemplatePageOutDto;
import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.coupontemplate.domain.repository.CouponTemplateQueryRepository;
import com.loopers.coupon.coupontemplate.domain.repository.vo.PageCriteria;
import com.loopers.coupon.coupontemplate.domain.repository.vo.PageResult;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class CouponTemplateQueryService {

	// repository
	private final CouponTemplateQueryRepository couponTemplateQueryRepository;


	/**
	 * 쿠폰 템플릿 조회 서비스
	 * 1. ID로 쿠폰 템플릿 조회 (없거나 삭제된 경우 예외)
	 * 2. ID로 쿠폰 템플릿 조회 (삭제 여부 무관)
	 * 3. 쿠폰 템플릿 목록 페이지 조회
	 */

	// 1. ID로 쿠폰 템플릿 조회 (없거나 삭제된 경우 예외)
	@Transactional(readOnly = true)
	public CouponTemplate getById(Long id) {
		CouponTemplate template = couponTemplateQueryRepository.findById(id)
			.orElseThrow(() -> new CoreException(ErrorType.COUPON_TEMPLATE_NOT_FOUND));

		if (template.isDeleted()) {
			throw new CoreException(ErrorType.COUPON_TEMPLATE_NOT_FOUND);
		}

		return template;
	}


	// 2. ID로 쿠폰 템플릿 조회 (삭제 여부 무관 — 쿠폰 적용 시 사용)
	@Transactional(readOnly = true)
	public CouponTemplate getByIdIncludeDeleted(Long id) {
		return couponTemplateQueryRepository.findById(id)
			.orElseThrow(() -> new CoreException(ErrorType.COUPON_TEMPLATE_NOT_FOUND));
	}


	// 3. 쿠폰 템플릿 목록 페이지 조회 (삭제되지 않은 것만)
	@Transactional(readOnly = true)
	public AdminCouponTemplatePageOutDto getAll(int page, int size) {

		// 페이지 조건 생성
		PageCriteria pageCriteria = new PageCriteria(page, size);

		// 목록 조회
		PageResult<CouponTemplate> result = couponTemplateQueryRepository.findAll(pageCriteria);

		// DTO 변환
		return new AdminCouponTemplatePageOutDto(
			result.content().stream().map(AdminCouponTemplateOutDto::from).toList(),
			result.page(),
			result.size(),
			result.totalElements()
		);
	}

}
