package com.loopers.ordering.order.infrastructure.repository;


import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.OrderItem;
import com.loopers.ordering.order.domain.repository.OrderCommandRepository;
import com.loopers.ordering.order.domain.repository.OrderQueryRepository;
import com.loopers.ordering.order.domain.repository.vo.PageCriteria;
import com.loopers.ordering.order.domain.repository.vo.PageResult;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("OrderQueryRepository 통합 테스트")
class OrderQueryRepositoryTest {

	@Autowired
	private OrderCommandRepository orderCommandRepository;

	@Autowired
	private OrderQueryRepository orderQueryRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Nested
	@DisplayName("findById")
	class FindById {

		@Test
		@DisplayName("[OrderQueryRepository.findById()] 존재하는 주문 ID -> Order + OrderItems 함께 반환")
		void findByIdExists() {
			// Arrange
			OrderItem item1 = OrderItem.create(1L, "상품A", new BigDecimal("10000"), 2L);
			OrderItem item2 = OrderItem.create(2L, "상품B", new BigDecimal("20000"), 1L);
			Order savedOrder = orderCommandRepository.save(
				Order.create(100L, "req-find-1", new BigDecimal("40000"), BigDecimal.ZERO, List.of(item1, item2), null)
			);

			// Act
			Optional<Order> result = orderQueryRepository.findById(savedOrder.getId());

			// Assert
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get().getId()).isEqualTo(savedOrder.getId()),
				() -> assertThat(result.get().getUserId()).isEqualTo(100L),
				() -> assertThat(result.get().getRequestId()).isEqualTo("req-find-1"),
				() -> assertThat(result.get().getTotalPrice()).isEqualByComparingTo(new BigDecimal("40000")),
				() -> assertThat(result.get().getItems()).hasSize(2),
				() -> assertThat(result.get().getCreatedAt()).isNotNull()
			);
		}


		@Test
		@DisplayName("[OrderQueryRepository.findById()] 존재하지 않는 ID -> Optional.empty 반환")
		void findByIdNotExists() {
			// Arrange
			Long nonExistentId = 999L;

			// Act
			Optional<Order> result = orderQueryRepository.findById(nonExistentId);

			// Assert
			assertThat(result).isEmpty();
		}
	}


	@Nested
	@DisplayName("findByUserId")
	class FindByUserId {

		@Test
		@DisplayName("[OrderQueryRepository.findByUserId()] 날짜 필터 없이 사용자별 주문 조회 -> 페이지네이션 정상 동작, 최신순 정렬")
		void findByUserIdWithoutDateFilter() {
			// Arrange
			Long userId = 100L;

			// 3개 주문 생성
			for (int i = 1; i <= 3; i++) {
				OrderItem item = OrderItem.create((long) i, "상품" + i, new BigDecimal("10000"), 1L);
				orderCommandRepository.save(
					Order.create(userId, "req-user-" + i, new BigDecimal("10000"), BigDecimal.ZERO, List.of(item), null)
				);
			}

			// 다른 사용자 주문 1개 생성
			OrderItem otherItem = OrderItem.create(99L, "다른상품", new BigDecimal("5000"), 1L);
			orderCommandRepository.save(
				Order.create(200L, "req-other-1", new BigDecimal("5000"), BigDecimal.ZERO, List.of(otherItem), null)
			);

			// Act
			PageResult<Order> result = orderQueryRepository.findByUserId(userId, null, null, new PageCriteria(0, 2));

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(2),
				() -> assertThat(result.totalElements()).isEqualTo(3),
				() -> assertThat(result.page()).isEqualTo(0),
				() -> assertThat(result.size()).isEqualTo(2),
				// 최신순 정렬 검증 (ID 내림차순)
				() -> assertThat(result.content().get(0).getId())
					.isGreaterThan(result.content().get(1).getId()),
				// 주문 항목도 함께 로딩 검증
				() -> assertThat(result.content()).allSatisfy(order ->
					assertThat(order.getItems()).isNotEmpty()
				)
			);
		}


		@Test
		@DisplayName("[OrderQueryRepository.findByUserId()] 날짜 필터 포함 사용자별 주문 조회 -> 해당 기간 주문만 반환")
		void findByUserIdWithDateFilter() {
			// Arrange
			Long userId = 100L;

			// 주문 2개 생성 (현재 시간에 생성됨)
			for (int i = 1; i <= 2; i++) {
				OrderItem item = OrderItem.create((long) i, "상품" + i, new BigDecimal("10000"), 1L);
				orderCommandRepository.save(
					Order.create(userId, "req-date-" + i, new BigDecimal("10000"), BigDecimal.ZERO, List.of(item), null)
				);
			}

			// Act: 오늘 날짜로 필터링
			java.time.LocalDate today = java.time.LocalDate.now();
			PageResult<Order> result = orderQueryRepository.findByUserId(userId, today, today, new PageCriteria(0, 20));

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(2),
				() -> assertThat(result.totalElements()).isEqualTo(2)
			);
		}


		@Test
		@DisplayName("[OrderQueryRepository.findByUserId()] 과거 날짜로 필터링 -> 빈 결과 반환. 해당 기간에 주문 없음")
		void findByUserIdWithPastDateFilter() {
			// Arrange
			Long userId = 100L;
			OrderItem item = OrderItem.create(1L, "상품", new BigDecimal("10000"), 1L);
			orderCommandRepository.save(
				Order.create(userId, "req-past-1", new BigDecimal("10000"), BigDecimal.ZERO, List.of(item), null)
			);

			// Act: 과거 날짜로 필터링
			java.time.LocalDate pastDate = java.time.LocalDate.of(2020, 1, 1);
			PageResult<Order> result = orderQueryRepository.findByUserId(userId, pastDate, pastDate, new PageCriteria(0, 20));

			// Assert
			assertAll(
				() -> assertThat(result.content()).isEmpty(),
				() -> assertThat(result.totalElements()).isEqualTo(0)
			);
		}
	}


	@Nested
	@DisplayName("findByUserIdAndRequestId")
	class FindByUserIdAndRequestId {

		@Test
		@DisplayName("[OrderQueryRepository.findByUserIdAndRequestId()] 존재하는 userId + requestId -> Order 반환")
		void findByUserIdAndRequestIdExists() {
			// Arrange
			OrderItem item = OrderItem.create(1L, "상품A", new BigDecimal("10000"), 2L);
			Order savedOrder = orderCommandRepository.save(
				Order.create(100L, "req-idempotent-1", new BigDecimal("20000"), BigDecimal.ZERO, List.of(item), null)
			);

			// Act
			Optional<Order> result = orderQueryRepository.findByUserIdAndRequestId(100L, "req-idempotent-1");

			// Assert
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get().getId()).isEqualTo(savedOrder.getId()),
				() -> assertThat(result.get().getUserId()).isEqualTo(100L),
				() -> assertThat(result.get().getRequestId()).isEqualTo("req-idempotent-1")
			);
		}


		@Test
		@DisplayName("[OrderQueryRepository.findByUserIdAndRequestId()] 존재하지 않는 requestId -> Optional.empty 반환")
		void findByUserIdAndRequestIdNotExists() {
			// Act
			Optional<Order> result = orderQueryRepository.findByUserIdAndRequestId(100L, "req-nonexistent");

			// Assert
			assertThat(result).isEmpty();
		}
	}


	@Nested
	@DisplayName("findAll")
	class FindAll {

		@Test
		@DisplayName("[OrderQueryRepository.findAll()] 전체 조회 -> 페이지네이션 정상 동작, 최신순 정렬")
		void findAllWithPagination() {
			// Arrange
			for (int i = 1; i <= 3; i++) {
				OrderItem item = OrderItem.create((long) i, "상품" + i, new BigDecimal("10000"), 1L);
				orderCommandRepository.save(
					Order.create((long) (i * 100), "req-all-" + i, new BigDecimal("10000"), BigDecimal.ZERO, List.of(item), null)
				);
			}

			// Act
			PageResult<Order> result = orderQueryRepository.findAll(new PageCriteria(0, 2));

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(2),
				() -> assertThat(result.totalElements()).isEqualTo(3),
				() -> assertThat(result.page()).isEqualTo(0),
				() -> assertThat(result.size()).isEqualTo(2),
				// 최신순 정렬 검증 (ID 내림차순)
				() -> assertThat(result.content().get(0).getId())
					.isGreaterThan(result.content().get(1).getId()),
				// 주문 항목도 함께 로딩 검증
				() -> assertThat(result.content()).allSatisfy(order ->
					assertThat(order.getItems()).isNotEmpty()
				)
			);
		}
	}

}
