package com.loopers.queue.waitingqueue.infrastructure.redis;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.config.redis.RedisConfig;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;


/**
 * WaitingQueueRedisAdapter 통합 테스트
 * - TestContainers Redis를 사용하여 실제 Redis 명령어 동작을 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@TestPropertySource(properties = {
    "queue.waiting.scheduler.enabled=false",
    "queue.waiting.consumer.enabled=false"
})
@DisplayName("WaitingQueueRedisAdapter 통합 테스트")
class WaitingQueueRedisAdapterTest {

    @Autowired
    private WaitingQueueRedisAdapter adapter;

    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    @Autowired
    private RedisTemplate<String, String> redisTemplate;


    @BeforeEach
    void setUp() {
        clearRedisKeys();
    }

    @AfterEach
    void tearDown() {
        clearRedisKeys();
    }

    private void clearRedisKeys() {
        // 대기열 관련 Redis 키 정리
        Set<String> queueKeys = redisTemplate.keys("waiting-queue*");
        if (queueKeys != null && !queueKeys.isEmpty()) {
            redisTemplate.delete(queueKeys);
        }
        Set<String> tokenKeys = redisTemplate.keys("entry-token:*");
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            redisTemplate.delete(tokenKeys);
        }
        Set<String> rejectedKeys = redisTemplate.keys("queue-rejected:*");
        if (rejectedKeys != null && !rejectedKeys.isEmpty()) {
            redisTemplate.delete(rejectedKeys);
        }
        Set<String> orderLockKeys = redisTemplate.keys("order-lock:*");
        if (orderLockKeys != null && !orderLockKeys.isEmpty()) {
            redisTemplate.delete(orderLockKeys);
        }
    }


    @Nested
    @DisplayName("addToQueue + getPosition")
    class AddToQueueAndGetPosition {

        @Test
        @DisplayName("[addToQueue()] 사용자 1명 추가 -> getPosition 0 반환. 첫 번째 사용자는 0번 순번")
        void addSingleUser_positionIsZero() {
            // Arrange
            Long userId = 100L;

            // Act
            adapter.addToQueue(userId);
            Long position = adapter.getPosition(userId);

            // Assert
            assertThat(position).isEqualTo(0);
        }

        @Test
        @DisplayName("[addToQueue()] 사용자 3명 순차 추가 -> 순번 0, 1, 2. INCR 기반 FIFO 순서 보장")
        void addThreeUsers_positionsAreInOrder() {
            // Arrange
            Long user1 = 101L;
            Long user2 = 102L;
            Long user3 = 103L;

            // Act
            adapter.addToQueue(user1);
            adapter.addToQueue(user2);
            adapter.addToQueue(user3);

            Long pos1 = adapter.getPosition(user1);
            Long pos2 = adapter.getPosition(user2);
            Long pos3 = adapter.getPosition(user3);

            // Assert
            assertAll(
                () -> assertThat(pos1).isEqualTo(0),
                () -> assertThat(pos2).isEqualTo(1),
                () -> assertThat(pos3).isEqualTo(2)
            );
        }

        @Test
        @DisplayName("[addToQueue()] 동일 사용자 두 번 추가 -> score 갱신되어 순번 변경. NX 미사용으로 재진입 시 뒤로 밀림")
        void addSameUserTwice_scoreUpdatedPositionChanges() {
            // Arrange
            Long user1 = 201L;
            Long user2 = 202L;

            // Act
            adapter.addToQueue(user1);
            adapter.addToQueue(user2);
            // user1 재진입 → score 갱신 → user2보다 뒤로 밀림
            adapter.addToQueue(user1);

            Long pos1 = adapter.getPosition(user1);
            Long pos2 = adapter.getPosition(user2);

            // Assert
            assertAll(
                () -> assertThat(pos2).isEqualTo(0),
                () -> assertThat(pos1).isEqualTo(1)
            );
        }
    }


    @Nested
    @DisplayName("getQueueSize")
    class GetQueueSize {

        @Test
        @DisplayName("[getQueueSize()] 빈 대기열 -> 0 반환. 초기 상태에서 대기 인원 없음")
        void emptyQueue_returnsZero() {
            // Arrange - 빈 상태

            // Act
            long size = adapter.getQueueSize();

            // Assert
            assertThat(size).isEqualTo(0);
        }

        @Test
        @DisplayName("[getQueueSize()] 사용자 3명 추가 -> 3 반환. ZCARD로 정확한 인원 수 조회")
        void threeUsersAdded_returnsThree() {
            // Arrange
            adapter.addToQueue(301L);
            adapter.addToQueue(302L);
            adapter.addToQueue(303L);

            // Act
            long size = adapter.getQueueSize();

            // Assert
            assertThat(size).isEqualTo(3);
        }
    }


    @Nested
    @DisplayName("removeFromQueue")
    class RemoveFromQueue {

        @Test
        @DisplayName("[removeFromQueue()] 대기열에 있는 사용자 제거 -> getPosition null 반환. ZREM으로 대기열에서 완전 제거")
        void removeExistingUser_positionBecomesNull() {
            // Arrange
            Long userId = 400L;
            adapter.addToQueue(userId);

            // Act
            adapter.removeFromQueue(userId);
            Long position = adapter.getPosition(userId);

            // Assert
            assertThat(position).isNull();
        }
    }


    @Nested
    @DisplayName("issueEntryTokens")
    class IssueEntryTokens {

        @Test
        @DisplayName("[issueEntryTokens()] 3명 대기 중 2명 발급 -> 2명의 userId 반환, 토큰 존재 확인. Lua 스크립트로 ZPOPMIN + SET EX 원자적 실행")
        void threeUsersInQueue_issueTwoTokens_returnsTwoUserIds() {
            // Arrange
            Long user1 = 501L;
            Long user2 = 502L;
            Long user3 = 503L;
            adapter.addToQueue(user1);
            adapter.addToQueue(user2);
            adapter.addToQueue(user3);

            // Act
            List<String> issuedUserIds = adapter.issueEntryTokens(2, 300);

            // Assert
            assertAll(
                () -> assertThat(issuedUserIds).hasSize(2),
                () -> assertThat(issuedUserIds).containsExactly(
                    user1.toString(), user2.toString()
                ),
                // 발급된 사용자의 토큰이 Redis에 존재
                () -> assertThat(adapter.getEntryToken(user1)).isNotNull(),
                () -> assertThat(adapter.getEntryToken(user2)).isNotNull(),
                // 발급되지 않은 사용자는 토큰 없음
                () -> assertThat(adapter.getEntryToken(user3)).isNull(),
                // 대기열에 1명만 남음
                () -> assertThat(adapter.getQueueSize()).isEqualTo(1)
            );
        }

        @Test
        @DisplayName("[issueEntryTokens()] 빈 대기열에서 발급 시도 -> 빈 리스트 반환. ZPOPMIN 대상 없음")
        void emptyQueue_issueTokens_returnsEmptyList() {
            // Arrange - 빈 상태

            // Act
            List<String> issuedUserIds = adapter.issueEntryTokens(18, 300);

            // Assert
            assertThat(issuedUserIds).isEmpty();
        }
    }


    @Nested
    @DisplayName("getEntryToken + deleteEntryToken")
    class EntryTokenLifecycle {

        @Test
        @DisplayName("[getEntryToken()] 토큰 발급 후 조회 -> entry-{UUID} 형식 반환. 예측 불가능한 UUID 기반 토큰")
        void afterIssue_getEntryToken_returnsTokenString() {
            // Arrange
            Long userId = 601L;
            adapter.addToQueue(userId);
            adapter.issueEntryTokens(1, 300);

            // Act
            String token = adapter.getEntryToken(userId);

            // Assert — entry- 접두사 + UUID 형식 (36자: 8-4-4-4-12)
            assertAll(
                () -> assertThat(token).isNotNull(),
                () -> assertThat(token).startsWith("entry-"),
                () -> assertThat(token).hasSize("entry-".length() + 36),
                () -> assertThat(token.substring("entry-".length()))
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
            );
        }


        @Test
        @DisplayName("[getEntryToken()] 연속 2회 토큰 발급 -> 서로 다른 토큰 값. UUID 기반이므로 매번 고유한 토큰 생성")
        void consecutiveIssues_produceDifferentTokens() {
            // Arrange
            Long user1 = 611L;
            Long user2 = 612L;
            adapter.addToQueue(user1);
            adapter.issueEntryTokens(1, 300);
            adapter.addToQueue(user2);
            adapter.issueEntryTokens(1, 300);

            // Act
            String token1 = adapter.getEntryToken(user1);
            String token2 = adapter.getEntryToken(user2);

            // Assert — 서로 다른 토큰
            assertAll(
                () -> assertThat(token1).isNotNull(),
                () -> assertThat(token2).isNotNull(),
                () -> assertThat(token1).isNotEqualTo(token2)
            );
        }


        @Test
        @DisplayName("[deleteEntryToken()] 토큰 삭제 후 조회 -> null 반환. DEL 명령으로 토큰 제거 확인")
        void afterDelete_getEntryToken_returnsNull() {
            // Arrange
            Long userId = 602L;
            adapter.addToQueue(userId);
            adapter.issueEntryTokens(1, 300);

            // Act
            adapter.deleteEntryToken(userId);
            String token = adapter.getEntryToken(userId);

            // Assert
            assertThat(token).isNull();
        }
    }


    @Nested
    @DisplayName("markRejected + isRejected")
    class RejectedStatus {

        @Test
        @DisplayName("[isRejected()] 거부 표시 전 -> false 반환. 초기 상태에서 거부 상태 아님")
        void notRejected_returnsFalse() {
            // Arrange
            Long userId = 801L;

            // Act
            boolean rejected = adapter.isRejected(userId);

            // Assert
            assertThat(rejected).isFalse();
        }

        @Test
        @DisplayName("[markRejected()] 거부 표시 후 -> isRejected true 반환. queue-rejected:{userId} 키에 TTL과 함께 저장")
        void afterMarkRejected_isRejectedReturnsTrue() {
            // Arrange
            Long userId = 802L;

            // Act
            adapter.markRejected(userId);
            boolean rejected = adapter.isRejected(userId);

            // Assert
            assertThat(rejected).isTrue();
        }
    }


    @Nested
    @DisplayName("동시 진입 순서 보장")
    class ConcurrentEntryOrderTest {

        @Test
        @DisplayName("[addToQueue()] 10개 스레드 동시 진입 -> 모든 유저의 position이 고유하고 0~9 범위. INCR 기반 FIFO 보장")
        void concurrentEntry_allPositionsUnique() throws InterruptedException {
            // Arrange
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            // Act - 10개 스레드가 동시에 addToQueue 호출
            for (int i = 0; i < threadCount; i++) {
                long userId = 900L + i;
                executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await(5, TimeUnit.SECONDS);
                        adapter.addToQueue(userId);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Assert - 모든 유저의 position이 0~9 범위 내 고유값
            Set<Long> positions = ConcurrentHashMap.newKeySet();
            for (int i = 0; i < threadCount; i++) {
                Long position = adapter.getPosition(900L + i);
                assertThat(position).isNotNull();
                positions.add(position);
            }

            assertAll(
                // 10개의 고유한 position
                () -> assertThat(positions).hasSize(threadCount),
                // 0~9 범위
                () -> assertThat(positions).allSatisfy(pos ->
                    assertThat(pos).isBetween(0L, 9L)
                ),
                // 대기열 크기 정확히 10
                () -> assertThat(adapter.getQueueSize()).isEqualTo(threadCount)
            );
        }
    }


    @Nested
    @DisplayName("토큰 발급 배치 안정성")
    class IssueEntryTokensBatchTest {

        @Test
        @DisplayName("[issueEntryTokens()] 대기열 5명인데 배치 18명 요청 -> 5명만 발급. 대기열 크기보다 큰 배치도 안전")
        void issueTokens_batchLargerThanQueue_issuesOnlyAvailable() {
            // Arrange - 5명 대기열 진입
            for (int i = 0; i < 5; i++) {
                adapter.addToQueue(1000L + i);
            }

            // Act - 대기열(5명)보다 큰 배치(18명) 요청
            List<String> issuedUserIds = adapter.issueEntryTokens(18, 300);

            // Assert
            assertAll(
                // 5명만 발급
                () -> assertThat(issuedUserIds).hasSize(5),
                // 대기열 비어있음
                () -> assertThat(adapter.getQueueSize()).isEqualTo(0),
                // 5명 모두 토큰 존재 확인
                () -> assertThat(adapter.getEntryToken(1000L)).isNotNull(),
                () -> assertThat(adapter.getEntryToken(1001L)).isNotNull(),
                () -> assertThat(adapter.getEntryToken(1002L)).isNotNull(),
                () -> assertThat(adapter.getEntryToken(1003L)).isNotNull(),
                () -> assertThat(adapter.getEntryToken(1004L)).isNotNull()
            );
        }

        @Test
        @DisplayName("[issueEntryTokens()] 연속 2회 배치 발급 -> 중복 없이 순서대로 발급. 첫 배치 후 남은 유저만 두 번째 배치에서 발급")
        void issueTokens_consecutiveBatches_noDuplicates() {
            // Arrange - 5명 대기열 진입
            for (int i = 0; i < 5; i++) {
                adapter.addToQueue(1100L + i);
            }

            // Act - 연속 2회 배치 발급
            List<String> firstBatch = adapter.issueEntryTokens(3, 300);
            List<String> secondBatch = adapter.issueEntryTokens(3, 300);

            // Assert
            Set<String> allIssuedIds = new HashSet<>();
            allIssuedIds.addAll(firstBatch);
            allIssuedIds.addAll(secondBatch);

            assertAll(
                // 첫 배치: 3명
                () -> assertThat(firstBatch).hasSize(3),
                // 두 번째 배치: 남은 2명
                () -> assertThat(secondBatch).hasSize(2),
                // 총 5명, 중복 없음
                () -> assertThat(allIssuedIds).hasSize(5),
                // 대기열 비어있음
                () -> assertThat(adapter.getQueueSize()).isEqualTo(0)
            );
        }
    }


    @Nested
    @DisplayName("주문 처리 락 (order-lock) 라이프사이클")
    class OrderLockLifecycleTest {

        @Test
        @DisplayName("[acquireOrderLock()] 최초 락 획득 -> UUID 반환. SET NX EX로 락 생성 성공")
        void acquireOrderLock_firstTime_returnsUuid() {
            // Arrange
            Long userId = 700L;

            // Act
            String lockValue = adapter.acquireOrderLock(userId, 300);

            // Assert
            assertThat(lockValue).isNotNull();
        }

        @Test
        @DisplayName("[acquireOrderLock()] 동일 사용자 중복 락 획득 시도 -> null 반환. 이미 락이 존재하므로 SET NX 실패")
        void acquireOrderLock_duplicate_returnsNull() {
            // Arrange
            Long userId = 701L;
            adapter.acquireOrderLock(userId, 300);

            // Act
            String acquiredAgain = adapter.acquireOrderLock(userId, 300);

            // Assert
            assertThat(acquiredAgain).isNull();
        }

        @Test
        @DisplayName("[releaseOrderLock()] 올바른 lockValue로 락 해제 후 재획득 -> UUID 반환. Lua compare-and-delete로 자신의 락만 해제")
        void releaseOrderLock_withCorrectValue_thenAcquireAgain_returnsUuid() {
            // Arrange
            Long userId = 702L;
            String lockValue = adapter.acquireOrderLock(userId, 300);

            // Act
            adapter.releaseOrderLock(userId, lockValue);
            String acquiredAgain = adapter.acquireOrderLock(userId, 300);

            // Assert
            assertThat(acquiredAgain).isNotNull();
        }
    }


    @Nested
    @DisplayName("Entry Token TTL 검증")
    class EntryTokenTtlTest {

        @Test
        @DisplayName("[issueEntryTokens()] TTL 1초로 발급 -> 즉시 존재 + TTL > 0 확인 후, 만료 시 null. Redis SET EX가 실제로 적용됨을 검증")
        void issuedEntryToken_hasTtlAndExpires() throws InterruptedException {
            // Arrange
            Long userId = 650L;
            adapter.addToQueue(userId);

            // Act — TTL 1초로 발급
            adapter.issueEntryTokens(1, 1);

            // Assert 1 — 즉시 토큰 존재 확인
            String token = adapter.getEntryToken(userId);
            assertThat(token).isNotNull();

            // Assert 2 — TTL이 실제로 설정되었는지 확인
            Long ttlMs = redisTemplate.getExpire("entry-token:" + userId, TimeUnit.MILLISECONDS);
            assertThat(ttlMs).isGreaterThan(0);

            // Assert 3 — 만료 대기 (50ms polling, 최대 3초)
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline) {
                if (adapter.getEntryToken(userId) == null) {
                    break;
                }
                Thread.sleep(50);
            }

            // Assert 4 — 만료 후 토큰 없음
            assertAll(
                () -> assertThat(adapter.getEntryToken(userId)).isNull(),
                () -> assertThat(redisTemplate.hasKey("entry-token:" + userId)).isFalse()
            );
        }
    }
}
