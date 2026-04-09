package com.loopers.ranking.interfaces.consumer;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.ranking.application.service.RankingScoreService;
import com.loopers.support.common.outbox.application.dto.KafkaEventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("RankingCollectorConsumer 단위 테스트")
class RankingCollectorConsumerTest {

	@Mock
	private RankingScoreService rankingScoreService;

	@Mock
	private Acknowledgment ack;

	private RankingCollectorConsumer consumer;
	private ObjectMapper objectMapper;


	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		objectMapper.findAndRegisterModules();
		consumer = new RankingCollectorConsumer(rankingScoreService, objectMapper);
	}


	@Nested
	@DisplayName("consume() 정상 흐름")
	class ConsumeTest {

		@Test
		@DisplayName("[consume()] PRODUCT_VIEWED 이벤트 -> viewDelta=1로 집계. ack 호출")
		void productViewed() throws Exception {
			// Arrange
			String data = objectMapper.writeValueAsString(Map.of("productId", 100L, "occurredAt", "2026-04-08T10:00:00"));
			KafkaEventEnvelope envelope = new KafkaEventEnvelope("evt-1", "PRODUCT_VIEWED", "PRODUCT", "100", data, LocalDateTime.now());
			List<ConsumerRecord<String, byte[]>> records = List.of(toRecord(envelope));

			given(rankingScoreService.findAlreadyHandledIds(anySet())).willReturn(Set.of());

			// Act
			consumer.consume(records, ack);

			// Assert
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<Long, long[]>> deltasCaptor = ArgumentCaptor.forClass(Map.class);
			verify(rankingScoreService).applyDeltas(deltasCaptor.capture(), anySet());

			Map<Long, long[]> deltas = deltasCaptor.getValue();
			assertThat(deltas).containsKey(100L);
			assertThat(deltas.get(100L)).isEqualTo(new long[]{1, 0, 0}); // [view, like, order]
			verify(ack).acknowledge();
		}

		@Test
		@DisplayName("[consume()] PRODUCT_LIKED 이벤트 -> likeDelta=1로 집계")
		void productLiked() throws Exception {
			// Arrange
			String data = objectMapper.writeValueAsString(Map.of("productLikeId", 1L, "userId", 1L, "productId", 200L, "occurredAt", "2026-04-08T10:00:00"));
			KafkaEventEnvelope envelope = new KafkaEventEnvelope("evt-2", "PRODUCT_LIKED", "PRODUCT", "200", data, LocalDateTime.now());

			given(rankingScoreService.findAlreadyHandledIds(anySet())).willReturn(Set.of());

			// Act
			consumer.consume(List.of(toRecord(envelope)), ack);

			// Assert
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<Long, long[]>> deltasCaptor = ArgumentCaptor.forClass(Map.class);
			verify(rankingScoreService).applyDeltas(deltasCaptor.capture(), anySet());
			assertThat(deltasCaptor.getValue().get(200L)).isEqualTo(new long[]{0, 1, 0});
			verify(ack).acknowledge();
		}

		@Test
		@DisplayName("[consume()] ORDER_PAID 이벤트 -> 상품별 orderDelta += quantity 집계")
		void orderPaid() throws Exception {
			// Arrange
			String data = objectMapper.writeValueAsString(Map.of(
				"items", List.of(
					Map.of("productId", 10L, "quantity", 3),
					Map.of("productId", 20L, "quantity", 1)
				)
			));
			KafkaEventEnvelope envelope = new KafkaEventEnvelope("evt-3", "ORDER_PAID", "ORDER", "1", data, LocalDateTime.now());

			given(rankingScoreService.findAlreadyHandledIds(anySet())).willReturn(Set.of());

			// Act
			consumer.consume(List.of(toRecord(envelope)), ack);

			// Assert
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<Long, long[]>> deltasCaptor = ArgumentCaptor.forClass(Map.class);
			verify(rankingScoreService).applyDeltas(deltasCaptor.capture(), anySet());

			Map<Long, long[]> deltas = deltasCaptor.getValue();
			assertThat(deltas.get(10L)).isEqualTo(new long[]{0, 0, 3});
			assertThat(deltas.get(20L)).isEqualTo(new long[]{0, 0, 1});
			verify(ack).acknowledge();
		}

		@Test
		@DisplayName("[consume()] 같은 상품에 대한 복수 이벤트 -> delta 합산")
		void sameProductMultipleEvents() throws Exception {
			// Arrange
			String viewData = objectMapper.writeValueAsString(Map.of("productId", 100L, "occurredAt", "2026-04-08T10:00:00"));
			String likeData = objectMapper.writeValueAsString(Map.of("productLikeId", 1L, "userId", 1L, "productId", 100L, "occurredAt", "2026-04-08T10:00:00"));

			List<ConsumerRecord<String, byte[]>> records = List.of(
				toRecord(new KafkaEventEnvelope("evt-1", "PRODUCT_VIEWED", "PRODUCT", "100", viewData, LocalDateTime.now())),
				toRecord(new KafkaEventEnvelope("evt-2", "PRODUCT_VIEWED", "PRODUCT", "100", viewData, LocalDateTime.now())),
				toRecord(new KafkaEventEnvelope("evt-3", "PRODUCT_LIKED", "PRODUCT", "100", likeData, LocalDateTime.now()))
			);

			given(rankingScoreService.findAlreadyHandledIds(anySet())).willReturn(Set.of());

			// Act
			consumer.consume(records, ack);

			// Assert
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<Long, long[]>> deltasCaptor = ArgumentCaptor.forClass(Map.class);
			verify(rankingScoreService).applyDeltas(deltasCaptor.capture(), anySet());
			assertThat(deltasCaptor.getValue().get(100L)).isEqualTo(new long[]{2, 1, 0});
		}
	}


	@Nested
	@DisplayName("멱등 필터링 테스트")
	class IdempotencyTest {

		@Test
		@DisplayName("[consume()] 이미 처리된 이벤트 -> 필터링하여 delta에서 제외")
		void alreadyHandledFiltered() throws Exception {
			// Arrange
			String data1 = objectMapper.writeValueAsString(Map.of("productId", 100L, "occurredAt", "2026-04-08T10:00:00"));
			String data2 = objectMapper.writeValueAsString(Map.of("productId", 200L, "occurredAt", "2026-04-08T10:00:00"));

			List<ConsumerRecord<String, byte[]>> records = List.of(
				toRecord(new KafkaEventEnvelope("evt-1", "PRODUCT_VIEWED", "PRODUCT", "100", data1, LocalDateTime.now())),
				toRecord(new KafkaEventEnvelope("evt-2", "PRODUCT_VIEWED", "PRODUCT", "200", data2, LocalDateTime.now()))
			);

			// evt-1은 이미 처리됨
			given(rankingScoreService.findAlreadyHandledIds(anySet())).willReturn(Set.of("evt-1"));

			// Act
			consumer.consume(records, ack);

			// Assert
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<Long, long[]>> deltasCaptor = ArgumentCaptor.forClass(Map.class);
			verify(rankingScoreService).applyDeltas(deltasCaptor.capture(), anySet());

			Map<Long, long[]> deltas = deltasCaptor.getValue();
			assertThat(deltas).containsKey(200L);
			assertThat(deltas).doesNotContainKey(100L);
		}

		@Test
		@DisplayName("[consume()] 모든 이벤트가 이미 처리됨 -> applyDeltas 미호출, ack만 수행")
		void allAlreadyHandled() throws Exception {
			// Arrange
			String data = objectMapper.writeValueAsString(Map.of("productId", 100L, "occurredAt", "2026-04-08T10:00:00"));
			List<ConsumerRecord<String, byte[]>> records = List.of(
				toRecord(new KafkaEventEnvelope("evt-1", "PRODUCT_VIEWED", "PRODUCT", "100", data, LocalDateTime.now()))
			);

			given(rankingScoreService.findAlreadyHandledIds(anySet())).willReturn(Set.of("evt-1"));

			// Act
			consumer.consume(records, ack);

			// Assert
			verify(rankingScoreService, never()).applyDeltas(any(), anySet());
			verify(ack).acknowledge();
		}
	}


	@Nested
	@DisplayName("에러 처리 테스트")
	class ErrorTest {

		@Test
		@DisplayName("[consume()] 처리 중 예외 -> RuntimeException 발생, ack 미호출")
		void exceptionThrown() throws Exception {
			// Arrange
			String data = objectMapper.writeValueAsString(Map.of("productId", 100L, "occurredAt", "2026-04-08T10:00:00"));
			List<ConsumerRecord<String, byte[]>> records = List.of(
				toRecord(new KafkaEventEnvelope("evt-1", "PRODUCT_VIEWED", "PRODUCT", "100", data, LocalDateTime.now()))
			);

			given(rankingScoreService.findAlreadyHandledIds(anySet())).willReturn(Set.of());
			doThrow(new RuntimeException("test error")).when(rankingScoreService).applyDeltas(any(), anySet());

			// Act & Assert
			assertThatThrownBy(() -> consumer.consume(records, ack))
				.isInstanceOf(RuntimeException.class);
			verify(ack, never()).acknowledge();
		}
	}


	// ConsumerRecord 생성 헬퍼
	private ConsumerRecord<String, byte[]> toRecord(KafkaEventEnvelope envelope) throws Exception {
		byte[] value = objectMapper.writeValueAsBytes(envelope);
		return new ConsumerRecord<>("test-topic", 0, 0, envelope.aggregateId(), value);
	}

}
