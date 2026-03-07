package com.loopers.coupon.issuedcoupon.infrastructure.jpa;


import com.loopers.coupon.issuedcoupon.infrastructure.entity.IssuedCouponEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


/**
 * 발급된 쿠폰 JPA 리포지토리
 */
public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCouponEntity, Long> {

	// 사용자 ID와 쿠폰 템플릿 ID로 발급 존재 여부 조회
	boolean existsByCouponTemplateIdAndUserId(Long couponTemplateId, Long userId);

	// 사용자 ID로 발급 쿠폰 목록 조회 (최신순)
	List<IssuedCouponEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

	// 쿠폰 템플릿 ID로 발급 쿠폰 목록 조회 (최신순)
	List<IssuedCouponEntity> findAllByCouponTemplateIdOrderByCreatedAtDesc(Long couponTemplateId);

	// ID로 발급 쿠폰 조회 (비관적 쓰기 락 — SELECT ... FOR UPDATE)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT ic FROM IssuedCouponEntity ic WHERE ic.id = :id")
	Optional<IssuedCouponEntity> findByIdForUpdate(@Param("id") Long id);

}
