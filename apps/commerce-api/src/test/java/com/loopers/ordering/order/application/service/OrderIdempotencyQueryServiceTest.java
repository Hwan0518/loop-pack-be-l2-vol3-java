package com.loopers.ordering.order.application.service;


import com.loopers.ordering.order.domain.model.IdempotencyKey;
import com.loopers.ordering.order.domain.repository.IdempotencyKeyQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
@DisplayName("OrderIdempotencyQueryService 테스트")
class OrderIdempotencyQueryServiceTest {

	@Mock
	private IdempotencyKeyQueryRepository idempotencyKeyQueryRepository;

	private OrderIdempotencyQueryService orderIdempotencyQueryService;

	@BeforeEach
	void setUp() {
		orderIdempotencyQueryService = new OrderIdempotencyQueryService(idempotencyKeyQueryRepository);
	}


	@Nested
	@DisplayName("findOrderIdByRequestId() 테스트")
	class FindOrderIdByRequestIdTest {

		@Test
		@DisplayName("[findOrderIdByRequestId()] 존재하는 멱등성 키 -> orderId 반환")
		void findExistingKey() {
			// Arrange
			Long userId = 1L;
			String requestId = "req-123";
			IdempotencyKey key = IdempotencyKey.reconstruct(1L, userId, requestId, 10L, LocalDateTime.now());

			given(idempotencyKeyQueryRepository.findByUserIdAndRequestId(userId, requestId))
				.willReturn(Optional.of(key));

			// Act
			Optional<Long> result = orderIdempotencyQueryService.findOrderIdByRequestId(userId, requestId);

			// Assert
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get()).isEqualTo(10L)
			);
		}

		@Test
		@DisplayName("[findOrderIdByRequestId()] 존재하지 않는 멱등성 키 -> Optional.empty 반환")
		void findNonExistingKey() {
			// Arrange
			given(idempotencyKeyQueryRepository.findByUserIdAndRequestId(1L, "req-999"))
				.willReturn(Optional.empty());

			// Act
			Optional<Long> result = orderIdempotencyQueryService.findOrderIdByRequestId(1L, "req-999");

			// Assert
			assertThat(result).isEmpty();
		}
	}

}
