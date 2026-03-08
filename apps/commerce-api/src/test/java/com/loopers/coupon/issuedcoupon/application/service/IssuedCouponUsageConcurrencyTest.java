package com.loopers.coupon.issuedcoupon.application.service;


import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
import com.loopers.coupon.coupontemplate.infrastructure.entity.CouponTemplateEntity;
import com.loopers.coupon.coupontemplate.infrastructure.jpa.CouponTemplateJpaRepository;
import com.loopers.coupon.issuedcoupon.application.dto.out.CouponApplyResult;
import com.loopers.coupon.issuedcoupon.application.facade.IssuedCouponCommandFacade;
import com.loopers.coupon.issuedcoupon.domain.model.enums.IssuedCouponStatus;
import com.loopers.coupon.issuedcoupon.infrastructure.entity.IssuedCouponEntity;
import com.loopers.coupon.issuedcoupon.infrastructure.jpa.IssuedCouponJpaRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("쿠폰 사용 동시성 통합 테스트")
class IssuedCouponUsageConcurrencyTest {

	@Autowired
	private IssuedCouponCommandFacade issuedCouponCommandFacade;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private CouponTemplateJpaRepository couponTemplateJpaRepository;

	@Autowired
	private IssuedCouponJpaRepository issuedCouponJpaRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Test
	@DisplayName("[applyToCoupon()] 동일 쿠폰 동시 적용 2건 -> 1건만 성공, 1건은 COUPON_ALREADY_USED 예외. 비관적 락으로 이중 사용 방지")
	void concurrentApplySameCoupon() throws Exception {
		// Arrange
		CouponTemplateEntity template = couponTemplateJpaRepository.save(
			CouponTemplateEntity.of("10% 할인", CouponType.RATE, new BigDecimal("10"),
				null, LocalDateTime.now().plusDays(30))
		);
		IssuedCouponEntity issuedCoupon = issuedCouponJpaRepository.save(
			IssuedCouponEntity.of(template.getId(), 100L, IssuedCouponStatus.AVAILABLE)
		);
		Long issuedCouponId = issuedCoupon.getId();

		int threadCount = 2;
		ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
		TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
		List<Future<CouponApplyResult>> futures = new ArrayList<>();

		// Act — 2개 스레드가 동시에 같은 쿠폰 적용 시도 (각 스레드가 주문 TX를 시뮬레이션)
		for (int i = 0; i < threadCount; i++) {
			futures.add(executorService.submit(() ->
				txTemplate.execute(status ->
					issuedCouponCommandFacade.applyToCoupon(issuedCouponId, 100L, new BigDecimal("30000"))
				)
			));
		}

		int successCount = 0;
		int failCount = 0;
		for (Future<CouponApplyResult> future : futures) {
			try {
				future.get(10, TimeUnit.SECONDS);
				successCount++;
			} catch (ExecutionException e) {
				// 실패 원인이 COUPON_ALREADY_USED인지 검증
				assertThat(e.getCause()).isInstanceOf(CoreException.class);
				assertThat(((CoreException) e.getCause()).getErrorType())
					.isEqualTo(ErrorType.COUPON_ALREADY_USED);
				failCount++;
			}
		}
		executorService.shutdown();

		// Assert
		assertThat(successCount).isEqualTo(1);
		assertThat(failCount).isEqualTo(1);

		// 최종 상태 검증 — 쿠폰이 USED로 변경됨
		IssuedCouponEntity result = issuedCouponJpaRepository.findById(issuedCouponId).orElseThrow();
		assertThat(result.getStatus()).isEqualTo(IssuedCouponStatus.USED);
	}

}
