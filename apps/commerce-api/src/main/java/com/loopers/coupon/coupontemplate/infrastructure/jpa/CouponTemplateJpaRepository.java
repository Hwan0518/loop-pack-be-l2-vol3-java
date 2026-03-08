package com.loopers.coupon.coupontemplate.infrastructure.jpa;


import com.loopers.coupon.coupontemplate.infrastructure.entity.CouponTemplateEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


/**
 * 쿠폰 템플릿 JPA 리포지토리
 */
public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplateEntity, Long> {

	// 삭제되지 않은 쿠폰 템플릿 목록 페이지 조회
	Page<CouponTemplateEntity> findAllByDeletedAtIsNull(Pageable pageable);

}
