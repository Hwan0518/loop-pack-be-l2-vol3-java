package com.loopers.cart.cart.infrastructure.repository;


import com.loopers.cart.cart.domain.model.CartItem;
import com.loopers.cart.cart.domain.repository.CartItemQueryRepository;
import com.loopers.cart.cart.infrastructure.jpa.CartItemJpaRepository;
import com.loopers.cart.cart.infrastructure.mapper.CartItemEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
@RequiredArgsConstructor
public class CartItemQueryRepositoryImpl implements CartItemQueryRepository {

	// jpa
	private final CartItemJpaRepository cartItemJpaRepository;
	// mapper
	private final CartItemEntityMapper cartItemMapper;


	/**
	 * 장바구니 항목 조회 리포지토리 구현체
	 * 1. ID로 장바구니 항목 조회
	 * 2. 사용자 ID로 장바구니 항목 목록 조회
	 * 3. 사용자 ID와 상품 ID로 장바구니 항목 조회
	 * 4. 사용자 ID로 선택된 장바구니 항목 목록 조회
	 * 5. 사용자 ID와 ID 목록으로 장바구니 항목 조회
	 */

	// 1. ID로 장바구니 항목 조회
	@Override
	public Optional<CartItem> findById(Long id) {
		return cartItemJpaRepository.findById(id)
			.map(cartItemMapper::toDomain);
	}


	// 2. 사용자 ID로 장바구니 항목 목록 조회
	@Override
	public List<CartItem> findByUserId(Long userId) {
		return cartItemJpaRepository.findByUserId(userId).stream()
			.map(cartItemMapper::toDomain)
			.toList();
	}


	// 3. 사용자 ID와 상품 ID로 장바구니 항목 조회
	@Override
	public Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId) {
		return cartItemJpaRepository.findByUserIdAndProductId(userId, productId)
			.map(cartItemMapper::toDomain);
	}


	// 4. 사용자 ID로 선택된 장바구니 항목 목록 조회
	@Override
	public List<CartItem> findSelectedByUserId(Long userId) {
		return cartItemJpaRepository.findByUserIdAndSelectedTrue(userId).stream()
			.map(cartItemMapper::toDomain)
			.toList();
	}


	// 5. 사용자 ID와 ID 목록으로 장바구니 항목 조회
	@Override
	public List<CartItem> findByUserIdAndIds(Long userId, List<Long> ids) {
		return cartItemJpaRepository.findByUserIdAndIdIn(userId, ids).stream()
			.map(cartItemMapper::toDomain)
			.toList();
	}

}
