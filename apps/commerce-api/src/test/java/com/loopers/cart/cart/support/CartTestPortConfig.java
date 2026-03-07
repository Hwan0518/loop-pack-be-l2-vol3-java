package com.loopers.cart.cart.support;


import com.loopers.cart.cart.application.port.out.client.catalog.CartProductReader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;


/**
 * Cart 테스트용 포트 구현체 설정
 * - E2E 테스트에서 Cross-BC 포트의 테스트 구현체를 제공
 */
@TestConfiguration
public class CartTestPortConfig {

	@Bean
	@Primary
	public CartProductReader testCartProductReader() {
		return productId -> {
			// 테스트용: 모든 상품을 유효하다고 간주
		};
	}

}
