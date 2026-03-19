package com.loopers.ordering.order.interfaces;


import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemInfo;
import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemReader;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductInfo;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductReader;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderStockManager;
import com.loopers.ordering.order.application.port.out.client.coupon.OrderCouponRestorer;
import com.loopers.ordering.order.application.port.out.client.payment.OrderPaymentReader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.util.List;


/**
 * E2E 테스트용 포트 Mock 설정
 * Cross-BC 의존성을 테스트용 빈으로 대체
 */
@TestConfiguration
public class OrderPortTestConfig {

	@Bean
	@Primary
	public OrderCartItemReader testOrderCartItemReader() {
		return new OrderCartItemReader() {
			@Override
			public List<OrderCartItemInfo> readSelectedCartItems(Long userId) {
				return List.of(
					new OrderCartItemInfo(1L, 1L, 2L),
					new OrderCartItemInfo(2L, 2L, 1L)
				);
			}

			@Override
			public List<OrderCartItemInfo> readCartItemsByIds(Long userId, List<Long> cartItemIds) {
				// 테스트용: cartItemIds에 해당하는 항목 반환
				return cartItemIds.stream()
					.map(id -> new OrderCartItemInfo(id, id, 2L))
					.toList();
			}
		};
	}

	@Bean
	@Primary
	public OrderProductReader testOrderProductReader() {
		return productIds -> productIds.stream()
			.map(productId -> new OrderProductInfo(
				productId,
				"테스트 상품 " + productId,
				new BigDecimal("10000").multiply(BigDecimal.valueOf(productId)),
				100L
			))
			.toList();
	}

	@Bean
	@Primary
	public OrderStockManager testOrderStockManager() {
		return new OrderStockManager() {
			@Override
			public void decreaseStock(Long productId, Long quantity) {
				// 테스트용: 재고 차감 성공
			}

			@Override
			public void restoreStock(Long productId, Long quantity) {
				// 테스트용: 재고 복원 성공
			}
		};
	}

	@Bean
	@Primary
	public OrderCouponRestorer testOrderCouponRestorer() {
		return issuedCouponId -> {
			// 테스트용: 쿠폰 복원 성공
		};
	}

	@Bean
	@Primary
	public OrderPaymentReader testOrderPaymentReader() {
		return orderId -> false; // 테스트용: 진행 중인 결제 없음
	}

}
