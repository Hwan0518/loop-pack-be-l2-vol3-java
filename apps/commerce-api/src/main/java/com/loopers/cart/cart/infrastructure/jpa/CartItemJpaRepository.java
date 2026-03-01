package com.loopers.cart.cart.infrastructure.jpa;


import com.loopers.cart.cart.infrastructure.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


public interface CartItemJpaRepository extends JpaRepository<CartItemEntity, Long> {

	/**
	 * 장바구니 항목 JPA 리포지토리
	 * 1. 사용자 ID로 장바구니 항목 목록 조회
	 * 2. 사용자 ID와 상품 ID로 장바구니 항목 조회
	 * 3. 사용자 ID로 선택된 장바구니 항목 목록 조회
	 * 4. 상품 ID로 장바구니 항목 전체 삭제
	 * 5. ID 목록으로 장바구니 항목 전체 삭제
	 * 6. 사용자 ID와 ID 목록으로 장바구니 항목 전체 삭제
	 * 7. 사용자 ID와 ID 목록으로 장바구니 항목 조회
	 */

	// 1. 사용자 ID로 장바구니 항목 목록 조회
	List<CartItemEntity> findByUserId(Long userId);

	// 2. 사용자 ID와 상품 ID로 장바구니 항목 조회
	Optional<CartItemEntity> findByUserIdAndProductId(Long userId, Long productId);

	// 3. 사용자 ID로 선택된 장바구니 항목 목록 조회
	List<CartItemEntity> findByUserIdAndSelectedTrue(Long userId);

	// 4. 상품 ID로 장바구니 항목 전체 삭제
	void deleteAllByProductId(Long productId);

	// 5. ID 목록으로 장바구니 항목 전체 삭제
	void deleteAllByIdIn(List<Long> ids);

	// 6. 사용자 ID와 ID 목록으로 장바구니 항목 전체 삭제
	void deleteAllByUserIdAndIdIn(Long userId, List<Long> ids);

	// 7. 사용자 ID와 ID 목록으로 장바구니 항목 조회
	List<CartItemEntity> findByUserIdAndIdIn(Long userId, List<Long> ids);

}
