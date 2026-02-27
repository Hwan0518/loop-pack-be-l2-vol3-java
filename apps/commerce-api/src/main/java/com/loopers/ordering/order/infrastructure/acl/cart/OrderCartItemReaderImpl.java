package com.loopers.ordering.order.infrastructure.acl.cart;


import com.loopers.cart.cart.application.facade.CartItemQueryFacade;
import com.loopers.cart.cart.domain.model.CartItem;
import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemInfo;
import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * ACL (Anti-Corruption Layer) - 주문 BC → 장바구니 BC 통합 구현체
 * 주문 생성 시 사용자의 장바구니 항목을 조회하는 역할 (Provider Facade에 위임)
 */
@Component
@RequiredArgsConstructor
public class OrderCartItemReaderImpl implements OrderCartItemReader {

	// facade: 장바구니 항목 조회 파사드 (cart BC)
	private final CartItemQueryFacade cartItemQueryFacade;


	/**
	 * 사용자의 장바구니 항목 조회
	 * 1. 사용자 ID로 선택된 장바구니 항목 조회
	 * 2. 사용자 ID와 장바구니 항목 ID 목록으로 조회
	 */

	// 1. 사용자의 선택된 장바구니 항목을 조회하여 주문용 정보로 변환
	@Override
	public List<OrderCartItemInfo> readSelectedCartItems(Long userId) {

		// facade: 선택된 장바구니 항목 목록 조회 (selected = true인 항목만)
		List<CartItem> selectedItems = cartItemQueryFacade.findSelectedByUserId(userId);

		// 장바구니 항목을 주문용 정보 레코드로 변환
		return toCartItemInfos(selectedItems);
	}


	// 2. 사용자의 특정 장바구니 항목 ID 목록으로 조회하여 주문용 정보로 변환
	@Override
	public List<OrderCartItemInfo> readCartItemsByIds(Long userId, List<Long> cartItemIds) {

		// facade: 사용자 ID와 장바구니 항목 ID 목록으로 조회
		List<CartItem> cartItems = cartItemQueryFacade.findByUserIdAndIds(userId, cartItemIds);

		// 장바구니 항목을 주문용 정보 레코드로 변환
		return toCartItemInfos(cartItems);
	}


	// 장바구니 항목 목록을 주문용 정보 레코드로 변환
	private List<OrderCartItemInfo> toCartItemInfos(List<CartItem> cartItems) {

		// CartItem → OrderCartItemInfo 변환 (도메인 모델에서 원시값 추출)
		return cartItems.stream()
			.map(item -> new OrderCartItemInfo(
				item.getId(),
				item.getProductId(),
				item.getQuantity().value()
			))
			.toList();
	}

}
