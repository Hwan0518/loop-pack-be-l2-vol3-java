package com.loopers.engagement.productlike.support;


import com.loopers.engagement.productlike.application.port.out.client.catalog.ProductLikeTargetValidator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;


/**
 * ProductLike 테스트용 포트 구현체 설정
 * - E2E 테스트에서 Cross-BC 포트의 테스트 구현체를 제공
 */
@TestConfiguration
public class ProductLikeTestPortConfig {

	@Bean
	@Primary
	public ProductLikeTargetValidator testProductLikeTargetValidator() {
		return targetId -> {
			// 테스트용: 모든 상품을 유효하다고 간주
		};
	}

}
