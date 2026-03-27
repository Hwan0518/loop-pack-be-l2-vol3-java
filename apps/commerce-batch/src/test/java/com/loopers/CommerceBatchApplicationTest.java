package com.loopers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@DisplayName("CommerceBatch 컨텍스트 테스트")
class CommerceBatchApplicationTest {

	@Test
	@DisplayName("[CommerceBatchApplicationTest] Spring Boot 애플리케이션 컨텍스트 로드 -> 모든 빈이 올바르게 로드됨")
	void contextLoads() {
	}
}
