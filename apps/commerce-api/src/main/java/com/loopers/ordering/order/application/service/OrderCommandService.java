package com.loopers.ordering.order.application.service;


import com.loopers.coupon.issuedcoupon.application.dto.out.CouponApplyResult;
import com.loopers.ordering.order.application.dto.in.OrderCreateInDto;
import com.loopers.ordering.order.application.dto.out.OrderDetailOutDto;
import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemCleaner;
import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemInfo;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductInfo;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderStockManager;
import com.loopers.ordering.order.application.port.out.client.coupon.OrderCouponApplier;
import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.OrderItem;
import com.loopers.ordering.order.domain.model.vo.CouponSnapshot;
import com.loopers.ordering.order.domain.repository.OrderCommandRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class OrderCommandService {

	// repository
	private final OrderCommandRepository orderCommandRepository;
	// port
	private final OrderStockManager orderStockManager;
	private final OrderCouponApplier orderCouponApplier;
	private final OrderCartItemCleaner orderCartItemCleaner;


	/**
	 * 주문 명령 서비스 (Facade에서 분리된 짧은 TX)
	 * 1. 주문 생성 (재고 차감 → 쿠폰 적용 → 주문 저장 → 장바구니 정리)
	 */

	// 1. 주문 생성 (짧은 TX — 재고 차감 → 쿠폰 적용 → 주문 저장 → 장바구니 정리)
	@Transactional
	public OrderDetailOutDto createOrder(Long userId, OrderCreateInDto inDto,
		List<OrderCartItemInfo> cartItems, List<OrderProductInfo> products,
		List<Long> resolvedCartItemIds) {

		// 재고 차감 (중복 합산 + productId 오름차순 — 데드락 방지)
		decreaseStocks(cartItems);

		// 쿠폰 적용 (쿠폰 미사용 시 할인 없음)
		BigDecimal originalTotalPrice = calculateTotalPrice(cartItems, products);
		CouponApplyResult couponApplyResult = inDto.couponId() != null
			? orderCouponApplier.apply(inDto.couponId(), userId, originalTotalPrice)
			: CouponApplyResult.noDiscount();

		// 주문 생성 + 저장 (requestId를 Order에 포함 — 유니크 제약으로 멱등성 보장)
		Order savedOrder = buildAndSaveOrder(userId, inDto.requestId(), cartItems, products, couponApplyResult);

		// 장바구니 정리
		orderCartItemCleaner.deleteCartItems(userId, resolvedCartItemIds);

		return OrderDetailOutDto.from(savedOrder);
	}


	/**
	 * private method
	 * - 재고 차감 (중복 합산 + productId 오름차순 정렬)
	 * - 총액 계산
	 * - 주문 생성 + 저장 (requestId 포함)
	 */

	// 재고 차감 (중복 합산 + productId 오름차순 정렬 — 데드락 방지)
	private void decreaseStocks(List<OrderCartItemInfo> cartItems) {

		// 중복 productId 수량 합산 후 오름차순 정렬 (락 획득 순서 고정 — 데드락 방지)
		Map<Long, Long> aggregated = cartItems.stream()
			.collect(Collectors.groupingBy(
				OrderCartItemInfo::productId,
				Collectors.summingLong(OrderCartItemInfo::quantity)
			));

		// productId 오름차순 정렬 후 재고 차감
		aggregated.entrySet().stream()
			.sorted(Comparator.comparingLong(Map.Entry::getKey))
			.forEach(entry -> orderStockManager.decreaseStock(entry.getKey(), entry.getValue()));
	}


	// 총액 계산
	private BigDecimal calculateTotalPrice(List<OrderCartItemInfo> cartItems, List<OrderProductInfo> products) {
		return cartItems.stream()
			.map(item -> {
				OrderProductInfo product = products.stream()
					.filter(p -> p.productId().equals(item.productId()))
					.findFirst()
					.orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
				return product.price().multiply(BigDecimal.valueOf(item.quantity()));
			})
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}


	// 주문 생성 + 저장
	private Order buildAndSaveOrder(Long userId, String requestId, List<OrderCartItemInfo> cartItems,
		List<OrderProductInfo> products, CouponApplyResult couponApplyResult) {

		// 상품 정보를 productId 기준으로 맵핑
		Map<Long, OrderProductInfo> productMap = products.stream()
			.collect(Collectors.toMap(OrderProductInfo::productId, p -> p));

		// 장바구니 항목으로부터 주문 항목 생성
		List<OrderItem> orderItems = cartItems.stream()
			.map(cartItem -> {
				OrderProductInfo product = Optional.ofNullable(productMap.get(cartItem.productId()))
					.orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
				return OrderItem.create(
					cartItem.productId(),
					product.name(),
					product.price(),
					cartItem.quantity()
				);
			})
			.toList();

		// 쿠폰 적용 전 원래 총액 계산
		BigDecimal originalTotalPrice = orderItems.stream()
			.map(OrderItem::getSubtotal)
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		// 쿠폰 스냅샷 생성 (쿠폰 적용된 경우만)
		CouponSnapshot couponSnapshot = couponApplyResult.hasDiscount()
			? new CouponSnapshot(
				couponApplyResult.issuedCouponId(),
				couponApplyResult.couponSnapshotName(),
				couponApplyResult.couponSnapshotType(),
				couponApplyResult.couponSnapshotValue()
			)
			: null;

		// 주문 생성 (쿠폰 할인 포함)
		Order order = Order.create(userId, requestId, originalTotalPrice, couponApplyResult.discountAmount(), orderItems, couponSnapshot);

		// 주문 저장
		return orderCommandRepository.save(order);
	}

}
