package com.loopers.cart.cart.infrastructure.repository;


import com.loopers.cart.cart.domain.model.CartItem;
import com.loopers.cart.cart.domain.repository.CartItemCommandRepository;
import com.loopers.cart.cart.infrastructure.entity.CartItemEntity;
import com.loopers.cart.cart.infrastructure.jpa.CartItemJpaRepository;
import com.loopers.cart.cart.infrastructure.mapper.CartItemEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
@RequiredArgsConstructor
public class CartItemCommandRepositoryImpl implements CartItemCommandRepository {

	// jpa
	private final CartItemJpaRepository cartItemJpaRepository;
	// mapper
	private final CartItemEntityMapper cartItemMapper;


	/**
	 * 장바구니 항목 명령 리포지토리 구현체
	 * 1. 장바구니 항목 저장
	 * 2. 장바구니 항목 삭제
	 * 3. 상품 ID로 장바구니 항목 전체 삭제
	 * 4. ID 목록으로 장바구니 항목 전체 삭제
	 * 5. 사용자 ID와 ID 목록으로 장바구니 항목 전체 삭제
	 */

	// 1. 장바구니 항목 저장
	@Override
	public CartItem save(CartItem cartItem) {
		CartItemEntity entity = cartItemMapper.toEntity(cartItem);
		CartItemEntity savedEntity = cartItemJpaRepository.save(entity);
		return cartItemMapper.toDomain(savedEntity);
	}


	// 2. 장바구니 항목 삭제
	@Override
	public void delete(CartItem cartItem) {
		cartItemJpaRepository.deleteById(cartItem.getId());
	}


	// 3. 상품 ID로 장바구니 항목 전체 삭제
	@Override
	public void deleteAllByProductId(Long productId) {
		cartItemJpaRepository.deleteAllByProductId(productId);
	}


	// 4. ID 목록으로 장바구니 항목 전체 삭제
	@Override
	public void deleteAllByIds(List<Long> ids) {
		if (ids != null && !ids.isEmpty()) {
			cartItemJpaRepository.deleteAllByIdIn(ids);
		}
	}


	// 5. 사용자 ID와 ID 목록으로 장바구니 항목 전체 삭제
	@Override
	public void deleteAllByUserIdAndIds(Long userId, List<Long> ids) {
		if (ids != null && !ids.isEmpty()) {
			cartItemJpaRepository.deleteAllByUserIdAndIdIn(userId, ids);
		}
	}

}
