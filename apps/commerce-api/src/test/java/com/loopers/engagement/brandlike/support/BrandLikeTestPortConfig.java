package com.loopers.engagement.brandlike.support;


import com.loopers.engagement.brandlike.application.port.out.client.catalog.BrandLikeTargetValidator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;


/**
 * BrandLike 테스트용 포트 구현체 설정
 * - E2E 테스트에서 Cross-BC 포트의 테스트 구현체를 제공
 */
@TestConfiguration
public class BrandLikeTestPortConfig {

	@Bean
	@Primary
	public BrandLikeTargetValidator testBrandLikeTargetValidator() {
		return targetId -> {
			// 테스트용: 모든 브랜드를 유효하다고 간주
		};
	}

}
