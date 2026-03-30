package com.loopers.coupon.application.service;


import com.loopers.coupon.infrastructure.entity.StreamerCouponIssueRequestEntity;
import com.loopers.coupon.infrastructure.entity.StreamerCouponTemplateEntity;
import com.loopers.coupon.infrastructure.jpa.StreamerCouponIssueRequestJpaRepository;
import com.loopers.coupon.infrastructure.jpa.StreamerCouponTemplateJpaRepository;
import com.loopers.coupon.infrastructure.jpa.StreamerIssuedCouponJpaRepository;
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

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(properties = {
	"spring.kafka.listener.auto-startup=false",
	"spring.kafka.bootstrap-servers=localhost:19092"
})
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("선착순 쿠폰 발급 동시성 테스트")
class CouponIssueConcurrencyTest {

	@Autowired
	private CouponIssueProcessorService couponIssueProcessorService;

	@Autowired
	private StreamerIssuedCouponJpaRepository issuedCouponJpaRepository;

	@Autowired
	private StreamerCouponTemplateJpaRepository couponTemplateJpaRepository;

	@Autowired
	private StreamerCouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Test
	@DisplayName("[processIssueRequest()] maxQuantity=10, 50건 순차 요청 -> 정확히 10건만 발급, 40건 COUPON_SOLD_OUT 거부. 단일 파티션 순차 처리 시뮬레이션")
	void sequentialIssueDoesNotExceedMaxQuantity() throws Exception {
		// Arrange
		int maxQuantity = 10;
		int totalRequests = 50;

		// 쿠폰 템플릿 직접 INSERT (maxQuantity=10, 만료=내일, 삭제 안 됨)
		Long templateId = insertCouponTemplate(maxQuantity);

		// 발급 요청 50건 생성 (각각 다른 userId, requestId)
		List<RequestInfo> requests = new ArrayList<>();
		for (int i = 0; i < totalRequests; i++) {
			String requestId = UUID.randomUUID().toString();
			Long userId = 1000L + i;
			String eventId = String.valueOf(i + 1);
			insertCouponIssueRequest(requestId, userId, templateId);
			requests.add(new RequestInfo(eventId, requestId, userId, templateId));
		}

		// Act — Kafka 단일 파티션 순차 처리 시뮬레이션 (설계 D3: 동시성 제어는 파티션 순차 처리로 보장)
		for (RequestInfo req : requests) {
			couponIssueProcessorService.processIssueRequest(
				req.eventId, req.requestId, req.userId, req.templateId);
		}

		// Assert — 정확히 maxQuantity 건만 발급
		long issuedCount = issuedCouponJpaRepository.countByCouponTemplateId(templateId);
		assertThat(issuedCount).isEqualTo(maxQuantity);

		// ISSUED/REJECTED 상태 + rejectReason 검증
		long issuedRequests = couponIssueRequestJpaRepository.findAll().stream()
			.filter(r -> "ISSUED".equals(r.getStatus()))
			.count();
		long soldOutCount = couponIssueRequestJpaRepository.findAll().stream()
			.filter(r -> "REJECTED".equals(r.getStatus()) && "COUPON_SOLD_OUT".equals(r.getRejectReason()))
			.count();

		assertThat(issuedRequests).isEqualTo(maxQuantity);
		assertThat(soldOutCount).isEqualTo(totalRequests - maxQuantity);
	}


	@Test
	@DisplayName("[processIssueRequest()] 동일 userId 중복 요청 -> 1건만 발급, 나머지 REJECTED(COUPON_ISSUE_DUPLICATED)")
	void duplicateUserIsRejected() {
		// Arrange
		Long templateId = insertCouponTemplate(100);
		Long userId = 1000L;

		String requestId1 = UUID.randomUUID().toString();
		String requestId2 = UUID.randomUUID().toString();
		insertCouponIssueRequest(requestId1, userId, templateId);
		insertCouponIssueRequest(requestId2, userId, templateId);

		// Act
		couponIssueProcessorService.processIssueRequest("1", requestId1, userId, templateId);
		couponIssueProcessorService.processIssueRequest("2", requestId2, userId, templateId);

		// Assert
		long issuedCount = issuedCouponJpaRepository.countByCouponTemplateId(templateId);
		assertThat(issuedCount).isEqualTo(1);

		StreamerCouponIssueRequestEntity rejected = couponIssueRequestJpaRepository.findByRequestId(requestId2).orElseThrow();
		assertThat(rejected.getStatus()).isEqualTo("REJECTED");
		assertThat(rejected.getRejectReason()).isEqualTo("COUPON_ISSUE_DUPLICATED");
	}


	// 헬퍼 — 쿠폰 템플릿 INSERT (reflection으로 필수 필드 설정)
	private Long insertCouponTemplate(int maxQuantity) {
		try {
			var constructor = StreamerCouponTemplateEntity.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			StreamerCouponTemplateEntity entity = constructor.newInstance();
			setField(entity, "id", 1L);
			setField(entity, "maxQuantity", maxQuantity);
			setField(entity, "expiredAt", LocalDateTime.now().plusDays(1));
			return couponTemplateJpaRepository.save(entity).getId();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// 헬퍼 — 발급 요청 INSERT
	private void insertCouponIssueRequest(String requestId, Long userId, Long templateId) {
		try {
			var constructor = StreamerCouponIssueRequestEntity.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			StreamerCouponIssueRequestEntity entity = constructor.newInstance();
			setField(entity, "requestId", requestId);
			setField(entity, "userId", userId);
			setField(entity, "couponTemplateId", templateId);
			setField(entity, "status", "PENDING");
			setField(entity, "createdAt", LocalDateTime.now());
			setField(entity, "updatedAt", LocalDateTime.now());
			couponIssueRequestJpaRepository.save(entity);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void setField(Object obj, String fieldName, Object value) throws Exception {
		Field field = obj.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(obj, value);
	}

	private record RequestInfo(String eventId, String requestId, Long userId, Long templateId) {}

}
