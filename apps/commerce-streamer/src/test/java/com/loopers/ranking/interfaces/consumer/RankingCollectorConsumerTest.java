package com.loopers.ranking.interfaces.consumer;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.ranking.application.dto.RankingDailyKey;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final LocalDateTime FIXED_OCCURRED_AT = LocalDateTime.of(2026, 4, 8, 10, 0, 0);
	private static final LocalDate EXPECTED_STAT_DATE = FIXED_OCCURRED_AT.atZone(KST).toLocalDate();


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
		@DisplayName("[consume()] PRODUCT_VIEWED 이벤트 -> persistDeltas + reflectToRedis 호출. ack 호출")
		void productViewed() throws Exception {
			// Arrange
			String data = objectMapper.writeValueAsString(Map.of("productId", 100L, "occurredAt", "2026-04-08T10:00:00"));
			KafkaEventEnvelope envelope = new KafkaEventEnvelope("evt-1", "PRODUCT_VIEWED", "PRODUCT", "100", data, FIXED_OCCURRED_AT);
			List<ConsumerRecord<String, byte[]>> records = List.of(toRecord(envelope));

			given(rankingScoreService.findAlreadyHandledIds(anySet())).willReturn(Set.of());

			// Act
			consumer.consume(records, ack);

			// Assert
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<RankingDailyKey, long[]>> deltasCaptor = ArgumentCaptor.forClass(Map.class);
			verify(rankingScoreService).persistDeltas(deltasCaptor.capture(), anySet());

			Map<RankingDailyKey, long[]> deltas = deltasCaptor.getValue();
			RankingDailyKey expectedKey = new RankingDailyKey(EXPECTED_STAT_DATE, 100L);
			assertThat(deltas).containsKey(expectedKey);
			assertThat(deltas.get(expectedKey)).isEqualTo(new long[]{1, 0, 0});

			verify(rankingScoreService).reflectToRedis(deltas);
			verify(ack).acknowledge();
		}

		@Test
		@DisplayName("[consume()] PRODUCT_LIKED 이벤트 -> likeDelta=1 집계")
		void productLiked() throws Exception {
			// Arrange
			String data = objectMapper.writeValueAsString(Map.of("productLikeId", 1L, "userId", 1L, "productId", 200L, "occurredAt", "2026-04-08T10:00:00"));
			KafkaEventEnvelope envelope = new KafkaEventEnvelope("evt-2", "PRODUCT_LIKED", "PRODUCT", "200", data, FIXED_OCCURRED_AT);

			given(rankingScoreService.findAlreadyHandledIds(anySet())).willReturn(Set.of());

			// Act
			consumer.consume(List.of(toRecord(envelope)), ack);

			// Assert
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<RankingDailyKey, long[]>> deltasCaptor = ArgumentCaptor.forClass(Map.class);
			verify(rankingScoreService).persistDeltas(deltasCaptor.capture(), anySet());
			assertThat(deltasCaptor.getValue().get(new RankingDailyKey(EXPECTED_STAT_DATE, 200L))).isEqualTo(new long[]{0, 1, 0});
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
			KafkaEventEnvelope envelope = new KafkaEventEnvelope("evt-3", "ORDER_PAID", "ORDER", "1", data, FIXED_OCCURRED_AT);

			given(rankingScoreService.findAlreadyHandledIds(anySet())).willReturn(Set.of());

			// Act
			consumer.consume(List.of(toRecord(envelope)), ack);

			// Assert
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<RankingDailyKey, long[]>> deltasCaptor = ArgumentCaptor.forClass(Map.class);
			verify(rankingScoreService).persistDeltas(deltasCaptor.capture(), anySet());

			Map<RankingDailyKey, long[]> deltas = deltasCaptor.getValue();
			assertThat(deltas.get(new RankingDailyKey(EXPECTED_STAT_DATE, 10L))).isEqualTo(new long[]{0, 0, 3});
			assertThat(deltas.get(new RankingDailyKey(EXPECTED_STAT_DATE, 20L))).isEqualTo(new long[]{0, 0, 1});
		}

		@Test
		@DisplayName("[consume()] 같은 상품에 대한 복수 이벤트 -> 동일 키 delta 합산")
		void sameProductMultipleEvents() throws Exception {
			// Arrange
			String viewData = objectMapper.writeValueAsString(Map.of("productId", 100L, "occurredAt", "2026-04-08T10:00:00"));
			String likeData = objectMapper.writeValueAsString(Map.of("productLikeId", 1L, "userId", 1L, "productId", 100L, "occurredAt", "2026-04-08T10:00:00"));

			List<ConsumerRecord<String, byte[]>> records = List.of(
				toRecord(new KafkaEventEnvelope("evt-1", "PRODUCT_VIEWED", "PRODUCT", "100", viewData, FIXED_OCCURRED_AT)),
				toRecord(new KafkaEventEnvelope("evt-2", "PRODUCT_VIEWED", "PRODUCT", "100", viewData, FIXED_OCCURRED_AT)),
				toRecord(new KafkaEventEnvelope("evt-3", "PRODUCT_LIKED", "PRODUCT", "100", likeData, FIXED_OCCURRED_AT))
			);

			given(rankingScoreService.findAlreadyHandledIds(anySet())).willReturn(Set.of());

			// Act
			consumer.consume(records, ack);

			// Assert
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<RankingDailyKey, long[]>> deltasCaptor = ArgumentCaptor.forClass(Map.class);
			verify(rankingScoreService).persistDeltas(deltasCaptor.capture(), anySet());
			assertThat(deltasCaptor.getValue().get(new RankingDailyKey(EXPECTED_STAT_DATE, 100L))).isEqualTo(new long[]{2, 1, 0});
		}

		@Test
		@DisplayName("[consume()] 같은 상품 다른 날짜 이벤트 -> 날짜별로 키 분리")
		void sameProductDifferentDates() throws Exception {
			// Arrange
			LocalDateTime occurredA = LocalDateTime.of(2026, 4, 8, 23, 0, 0);
			LocalDateTime occurredB = LocalDateTime.of(2026, 4, 9, 1, 0, 0);
			LocalDate dateA = occurredA.atZone(KST).toLocalDate();
			LocalDate dateB = occurredB.atZone(KST).toLocalDate();

			String viewData = objectMapper.writeValueAsString(Map.of("productId", 100L, "occurredAt", "2026-04-08T23:00:00"));

			List<ConsumerRecord<String, byte[]>> records = List.of(
				toRecord(new KafkaEventEnvelope("evt-1", "PRODUCT_VIEWED", "PRODUCT", "100", viewData, occurredA)),
				toRecord(new KafkaEventEnvelope("evt-2", "PRODUCT_VIEWED", "PRODUCT", "100", viewData, occurredB))
			);

			given(rankingScoreService.findAlreadyHandledIds(anySet())).willReturn(Set.of());

			// Act
			consumer.consume(records, ack);

			// Assert
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<RankingDailyKey, long[]>> deltasCaptor = ArgumentCaptor.forClass(Map.class);
			verify(rankingScoreService).persistDeltas(deltasCaptor.capture(), anySet());

			Map<RankingDailyKey, long[]> deltas = deltasCaptor.getValue();
			assertThat(deltas).hasSize(2);
			assertThat(deltas.get(new RankingDailyKey(dateA, 100L))).isEqualTo(new long[]{1, 0, 0});
			assertThat(deltas.get(new RankingDailyKey(dateB, 100L))).isEqualTo(new long[]{1, 0, 0});
		}
	}


	@Nested
	@DisplayName("Redis 실패 테스트")
	class RedisFailureTest {

		@Test
		@DisplayName("[consume()] Redis 반영 실패 -> dirty mark 호출, ack 정상")
		void redisFailureMarksDirty() throws Exception {
			// Arrange
			String data = objectMapper.writeValueAsString(Map.of("productId", 100L, "occurredAt", "2026-04-08T10:00:00"));
			KafkaEventEnvelope envelope = new KafkaEventEnvelope("evt-1", "PRODUCT_VIEWED", "PRODUCT", "100", data, FIXED_OCCURRED_AT);

			given(rankingScoreService.findAlreadyHandledIds(anySet())).willReturn(Set.of());
			doThrow(new RuntimeException("Redis down")).when(rankingScoreService).reflectToRedis(any());

			// Act
			consumer.consume(List.of(toRecord(envelope)), ack);

			// Assert — persistDeltas 호출됨, dirty mark 호출됨, ack 정상
			verify(rankingScoreService).persistDeltas(any(), anySet());
			verify(rankingScoreService).markProjectionDirty(Set.of(EXPECTED_STAT_DATE));
			verify(ack).acknowledge();
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
				toRecord(new KafkaEventEnvelope("evt-1", "PRODUCT_VIEWED", "PRODUCT", "100", data1, FIXED_OCCURRED_AT)),
				toRecord(new KafkaEventEnvelope("evt-2", "PRODUCT_VIEWED", "PRODUCT", "200", data2, FIXED_OCCURRED_AT))
			);

			given(rankingScoreService.findAlreadyHandledIds(anySet())).willReturn(Set.of("evt-1"));

			// Act
			consumer.consume(records, ack);

			// Assert
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<RankingDailyKey, long[]>> deltasCaptor = ArgumentCaptor.forClass(Map.class);
			verify(rankingScoreService).persistDeltas(deltasCaptor.capture(), anySet());

			Map<RankingDailyKey, long[]> deltas = deltasCaptor.getValue();
			assertThat(deltas).containsKey(new RankingDailyKey(EXPECTED_STAT_DATE, 200L));
			assertThat(deltas).doesNotContainKey(new RankingDailyKey(EXPECTED_STAT_DATE, 100L));
		}

		@Test
		@DisplayName("[consume()] 같은 eventId가 같은 배치에 중복 전달 -> delta와 handled id는 1회만 반영")
		void duplicateEventIdInSameBatchCountedOnce() throws Exception {
			// Arrange
			String data = objectMapper.writeValueAsString(Map.of("productId", 100L, "occurredAt", "2026-04-08T10:00:00"));
			List<ConsumerRecord<String, byte[]>> records = List.of(
				toRecord(new KafkaEventEnvelope("evt-dup", "PRODUCT_VIEWED", "PRODUCT", "100", data, FIXED_OCCURRED_AT)),
				toRecord(new KafkaEventEnvelope("evt-dup", "PRODUCT_VIEWED", "PRODUCT", "100", data, FIXED_OCCURRED_AT))
			);

			given(rankingScoreService.findAlreadyHandledIds(anySet())).willReturn(Set.of());

			// Act
			consumer.consume(records, ack);

			// Assert
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<RankingDailyKey, long[]>> deltasCaptor = ArgumentCaptor.forClass(Map.class);
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Set<String>> eventIdsCaptor = ArgumentCaptor.forClass(Set.class);
			verify(rankingScoreService).persistDeltas(deltasCaptor.capture(), eventIdsCaptor.capture());

			Map<RankingDailyKey, long[]> deltas = deltasCaptor.getValue();
			assertThat(deltas).hasSize(1);
			assertThat(deltas.get(new RankingDailyKey(EXPECTED_STAT_DATE, 100L))).isEqualTo(new long[]{1, 0, 0});
			assertThat(eventIdsCaptor.getValue()).containsExactly("evt-dup");
			verify(ack).acknowledge();
		}

		@Test
		@DisplayName("[consume()] 모든 이벤트가 이미 처리됨 -> persistDeltas 미호출, ack만 수행")
		void allAlreadyHandled() throws Exception {
			// Arrange
			String data = objectMapper.writeValueAsString(Map.of("productId", 100L, "occurredAt", "2026-04-08T10:00:00"));
			List<ConsumerRecord<String, byte[]>> records = List.of(
				toRecord(new KafkaEventEnvelope("evt-1", "PRODUCT_VIEWED", "PRODUCT", "100", data, FIXED_OCCURRED_AT))
			);

			given(rankingScoreService.findAlreadyHandledIds(anySet())).willReturn(Set.of("evt-1"));

			// Act
			consumer.consume(records, ack);

			// Assert
			verify(rankingScoreService, never()).persistDeltas(any(), anySet());
			verify(ack).acknowledge();
		}
	}


	@Nested
	@DisplayName("에러 처리 테스트")
	class ErrorTest {

		@Test
		@DisplayName("[consume()] DB 처리 중 예외 -> RuntimeException 발생, ack 미호출")
		void dbExceptionThrown() throws Exception {
			// Arrange
			String data = objectMapper.writeValueAsString(Map.of("productId", 100L, "occurredAt", "2026-04-08T10:00:00"));
			List<ConsumerRecord<String, byte[]>> records = List.of(
				toRecord(new KafkaEventEnvelope("evt-1", "PRODUCT_VIEWED", "PRODUCT", "100", data, FIXED_OCCURRED_AT))
			);

			given(rankingScoreService.findAlreadyHandledIds(anySet())).willReturn(Set.of());
			doThrow(new RuntimeException("DB error")).when(rankingScoreService).persistDeltas(any(), anySet());

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
