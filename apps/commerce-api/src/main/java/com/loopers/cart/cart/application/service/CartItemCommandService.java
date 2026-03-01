package com.loopers.cart.cart.application.service;


import com.loopers.cart.cart.application.dto.in.CartItemAddInDto;
import com.loopers.cart.cart.application.dto.in.CartItemSelectionInDto;
import com.loopers.cart.cart.application.dto.in.CartItemUpdateQuantityInDto;
import com.loopers.cart.cart.application.dto.out.CartItemSelectionOutDto;
import com.loopers.cart.cart.application.port.out.client.catalog.CartProductReader;
import com.loopers.cart.cart.application.port.out.client.user.UserAuthenticator;
import com.loopers.cart.cart.domain.model.CartItem;
import com.loopers.cart.cart.domain.repository.CartItemCommandRepository;
import com.loopers.cart.cart.domain.repository.CartItemQueryRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;


@Service
@RequiredArgsConstructor
public class CartItemCommandService {

	// repository
	private final CartItemCommandRepository cartItemCommandRepository;
	private final CartItemQueryRepository cartItemQueryRepository;
	// port
	private final CartProductReader cartProductReader;
	private final UserAuthenticator userAuthenticator;


	/**
	 * 장바구니 항목 명령 서비스
	 * 1. 사용자 인증
	 * 2. 장바구니 항목 추가
	 * 3. 장바구니 항목 수량 변경
	 * 4. 장바구니 항목 삭제
	 * 5. 장바구니 항목 선택 상태 변경
	 * 6. 상품 ID로 장바구니 항목 전체 삭제
	 * 7. ID 목록으로 장바구니 항목 전체 삭제
	 * 8. 사용자 ID와 ID 목록으로 장바구니 항목 전체 삭제
	 */

	// 1. 사용자 인증
	@Transactional(readOnly = true)
	public Long authenticate(String loginId, String password) {
		return userAuthenticator.authenticate(loginId, password);
	}

	// 2. 장바구니 항목 추가
	@Transactional
	public CartItem addItem(Long userId, CartItemAddInDto inDto) {

		// 상품 존재 여부 검증 (Provider 예외 → Consumer 에러 매핑)
		try {
			cartProductReader.validateProductExists(inDto.productId());
		} catch (CoreException e) {
			throw new CoreException(ErrorType.CART_PRODUCT_NOT_FOUND);
		}

		// 기존 항목 조회
		Optional<CartItem> existing = cartItemQueryRepository
			.findByUserIdAndProductId(userId, inDto.productId());

		// 기존 항목 존재 시 수량 추가
		if (existing.isPresent()) {
			CartItem cartItem = existing.get();
			cartItem.addQuantity(inDto.quantity());
			return cartItemCommandRepository.save(cartItem);
		}

		// 신규 항목 생성
		CartItem cartItem = CartItem.create(userId, inDto.productId(), inDto.quantity());
		return cartItemCommandRepository.save(cartItem);
	}


	// 3. 장바구니 항목 수량 변경
	@Transactional
	public CartItem updateQuantity(Long cartItemId, Long userId, CartItemUpdateQuantityInDto inDto) {

		// 장바구니 항목 조회 (소유권 검증 포함)
		CartItem cartItem = getOwnedCartItem(cartItemId, userId);

		// 수량 변경
		cartItem.changeQuantity(inDto.quantity());

		// 저장
		return cartItemCommandRepository.save(cartItem);
	}


	// 4. 장바구니 항목 삭제
	@Transactional
	public void deleteItem(Long cartItemId, Long userId) {

		// 장바구니 항목 조회 (소유권 검증 포함)
		CartItem cartItem = getOwnedCartItem(cartItemId, userId);

		// 삭제
		cartItemCommandRepository.delete(cartItem);
	}


	// 5. 장바구니 항목 선택 상태 변경
	@Transactional
	public CartItemSelectionOutDto updateSelection(Long userId, CartItemSelectionInDto inDto) {

		// 사용자의 전체 장바구니 항목 조회
		List<CartItem> allItems = cartItemQueryRepository.findByUserId(userId);

		// 선택 대상 ID 집합
		Set<Long> selectedIds = new HashSet<>(inDto.selectedCartItemIds());

		// 선택/해제 분류
		List<Long> selected = new ArrayList<>();
		List<Long> deselected = new ArrayList<>();

		for (CartItem item : allItems) {
			if (selectedIds.contains(item.getId())) {
				item.select();
				selected.add(item.getId());
			} else {
				item.deselect();
				deselected.add(item.getId());
			}
			cartItemCommandRepository.save(item);
		}

		return new CartItemSelectionOutDto(selected, deselected);
	}


	// 6. 상품 ID로 장바구니 항목 전체 삭제
	@Transactional
	public void deleteAllByProductId(Long productId) {
		cartItemCommandRepository.deleteAllByProductId(productId);
	}


	// 7. ID 목록으로 장바구니 항목 전체 삭제
	@Transactional
	public void deleteAllByIds(List<Long> ids) {
		cartItemCommandRepository.deleteAllByIds(ids);
	}


	// 8. 사용자 ID와 ID 목록으로 장바구니 항목 전체 삭제
	@Transactional
	public void deleteAllByUserIdAndIds(Long userId, List<Long> ids) {
		cartItemCommandRepository.deleteAllByUserIdAndIds(userId, ids);
	}


	/**
	 * private 메서드
	 * 1. 소유권 검증 포함 장바구니 항목 조회
	 */

	// 1. 소유권 검증 포함 장바구니 항목 조회
	private CartItem getOwnedCartItem(Long cartItemId, Long userId) {

		// 장바구니 항목 조회
		CartItem cartItem = cartItemQueryRepository.findById(cartItemId)
			.orElseThrow(() -> new CoreException(ErrorType.CART_ITEM_NOT_FOUND));

		// 소유권 검증 (불일치 시 존재하지 않는 것처럼 처리 - 보안)
		if (!cartItem.getUserId().equals(userId)) {
			throw new CoreException(ErrorType.CART_ITEM_NOT_FOUND);
		}

		return cartItem;
	}

}
