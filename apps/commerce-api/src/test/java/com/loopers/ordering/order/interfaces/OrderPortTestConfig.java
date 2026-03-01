package com.loopers.ordering.order.interfaces;


import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemInfo;
import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemReader;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductInfo;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductReader;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderStockManager;
import com.loopers.ordering.order.application.port.out.client.user.UserAuthenticator;
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
	public UserAuthenticator testOrderUserAuthenticator() {
		return (loginId, password) -> {
			// 테스트용: loginId를 해시하여 userId로 사용
			return (long) loginId.hashCode();
		};
	}

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
		return (productId, quantity) -> {
			// 테스트용: 재고 차감 성공
		};
	}

}
