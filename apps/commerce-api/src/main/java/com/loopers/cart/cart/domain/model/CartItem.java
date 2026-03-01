package com.loopers.cart.cart.domain.model;


import com.loopers.cart.cart.domain.model.vo.Quantity;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;


@Getter
public class CartItem {

	/**
	 * 장바구니 항목
	 * - id: 장바구니 항목 ID
	 * - userId: 사용자 ID
	 * - productId: 상품 ID
	 * - quantity: 수량
	 * - selected: 선택 여부 (기본값 true)
	 * - createdAt: 생성 일시
	 * - updatedAt: 수정 일시
	 */

	private final Long id;
	private final Long userId;
	private final Long productId;
	private Quantity quantity;
	private boolean selected;
	private final LocalDateTime createdAt;
	private final LocalDateTime updatedAt;


	// 생성자
	private CartItem(Long id, Long userId, Long productId, Quantity quantity,
		boolean selected, LocalDateTime createdAt, LocalDateTime updatedAt) {
		this.id = id;
		this.userId = userId;
		this.productId = productId;
		this.quantity = quantity;
		this.selected = selected;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}


	/**
	 * 도메인 로직
	 * 1. 장바구니 항목 생성
	 * 2. 장바구니 항목 재생성 (Entity -> Model 매핑용도)
	 * 3. 수량 추가
	 * 4. 수량 변경
	 * 5. 선택
	 * 6. 선택 해제
	 */

	// 1. 장바구니 항목 생성 (신규 항목 생성, 기본 선택 상태: true)
	public static CartItem create(Long userId, Long productId, Long quantity) {

		// null 검증
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(productId, "productId");

		// 수량 VO 생성 (내부에서 양수 검증)
		Quantity qty = Quantity.create(quantity);

		return new CartItem(null, userId, productId, qty, true, null, null);
	}


	// 2. 장바구니 항목 재생성 (Entity -> Model 매핑용도)
	public static CartItem reconstruct(Long id, Long userId, Long productId,
		Quantity quantity, boolean selected, LocalDateTime createdAt, LocalDateTime updatedAt) {
		return new CartItem(id, userId, productId, quantity, selected, createdAt, updatedAt);
	}


	// 3. 수량 추가
	public void addQuantity(Long amount) {
		this.quantity = this.quantity.add(amount);
	}


	// 4. 수량 변경
	public void changeQuantity(Long quantity) {
		this.quantity = Quantity.create(quantity);
	}


	// 5. 선택
	public void select() {
		this.selected = true;
	}


	// 6. 선택 해제
	public void deselect() {
		this.selected = false;
	}

}
