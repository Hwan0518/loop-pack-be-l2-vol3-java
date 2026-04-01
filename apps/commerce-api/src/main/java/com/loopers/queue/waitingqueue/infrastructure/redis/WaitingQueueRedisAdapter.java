package com.loopers.queue.waitingqueue.infrastructure.redis;


import com.loopers.config.redis.RedisConfig;
import com.loopers.queue.waitingqueue.application.port.out.WaitingQueueRedisPort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;


/**
 * 대기열 Redis 어댑터
 * - master-only RedisTemplate 사용 (stale read 방지)
 * - waiting-queue: Sorted Set (score = INCR 기반 전역 단조 증가)
 * - waiting-queue-seq: 전역 순번 카운터
 * - entry-token:{userId}: 입장 토큰
 */
@Component
public class WaitingQueueRedisAdapter implements WaitingQueueRedisPort {

	private static final String QUEUE_KEY = "waiting-queue";
	private static final String SEQ_KEY = "waiting-queue-seq";
	private static final String ENTRY_TOKEN_PREFIX = "entry-token:";
	private static final String REJECTED_PREFIX = "queue-rejected:";

	private final RedisTemplate<String, String> redisTemplate;


	public WaitingQueueRedisAdapter(
		@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
	) {
		this.redisTemplate = redisTemplate;
	}


	// 1. 대기열 적재 (INCR → ZADD)
	@Override
	public long addToQueue(Long userId) {
		// 전역 단조 증가 순번 생성
		Long score = redisTemplate.opsForValue().increment(SEQ_KEY);

		// NX 미사용: 재진입 시 score 갱신 → 뒤로 밀림 (새로고침 억제)
		redisTemplate.opsForZSet().add(QUEUE_KEY, userId.toString(), score);

		return score;
	}


	// 2. 순번 조회 (ZRANK)
	@Override
	public Long getPosition(Long userId) {
		return redisTemplate.opsForZSet().rank(QUEUE_KEY, userId.toString());
	}


	// 3. 전체 대기 인원 (ZCARD)
	@Override
	public long getQueueSize() {
		Long size = redisTemplate.opsForZSet().zCard(QUEUE_KEY);
		return size != null ? size : 0;
	}


	// 4. 대기열 제거 (ZREM)
	@Override
	public void removeFromQueue(Long userId) {
		redisTemplate.opsForZSet().remove(QUEUE_KEY, userId.toString());
	}


	// 5. 입장 토큰 조회 (GET entry-token:{userId})
	@Override
	public String getEntryToken(Long userId) {
		return redisTemplate.opsForValue().get(ENTRY_TOKEN_PREFIX + userId);
	}


	// 6. 입장 토큰 발급 — Lua script (ZPOPMIN + SET EX, 원자적)
	@Override
	public List<String> issueEntryTokens(int batchSize, long tokenTtlSeconds) {
		String luaScript =
			"local users = redis.call('ZPOPMIN', KEYS[1], ARGV[1]) " +
			"local results = {} " +
			"for i = 1, #users, 2 do " +
			"  local userId = users[i] " +
			"  local token = ARGV[2] .. userId " +
			"  redis.call('SET', 'entry-token:' .. userId, token, 'EX', ARGV[3]) " +
			"  table.insert(results, userId) " +
			"end " +
			"return results";

		DefaultRedisScript<List> script = new DefaultRedisScript<>(luaScript, List.class);
		String tokenPrefix = "entry-token-";

		@SuppressWarnings("unchecked")
		List<String> userIds = (List<String>) redisTemplate.execute(
			script,
			Collections.singletonList(QUEUE_KEY),
			String.valueOf(batchSize),
			tokenPrefix,
			String.valueOf(tokenTtlSeconds)
		);

		return userIds != null ? userIds : new ArrayList<>();
	}


	// 7. 입장 토큰 삭제 (DEL entry-token:{userId})
	@Override
	public void deleteEntryToken(Long userId) {
		redisTemplate.delete(ENTRY_TOKEN_PREFIX + userId);
	}


	// 8. 입장 토큰 원자적 소비 (GETDEL — 검증 + 삭제를 한 번에)
	@Override
	public String consumeEntryToken(Long userId) {
		String key = ENTRY_TOKEN_PREFIX + userId;
		// Lua script: GET + DEL 원자적 실행
		String luaScript = "local v = redis.call('GET', KEYS[1]) " +
			"if v then redis.call('DEL', KEYS[1]) end " +
			"return v";
		DefaultRedisScript<String> script = new DefaultRedisScript<>(luaScript, String.class);
		return redisTemplate.execute(script, java.util.Collections.singletonList(key));
	}


	// 9. cap 초과 거부 상태 저장 (60초 TTL)
	@Override
	public void markRejected(Long userId) {
		redisTemplate.opsForValue().set(
			REJECTED_PREFIX + userId, "1", java.time.Duration.ofSeconds(60)
		);
	}


	// 9. cap 초과 거부 상태 확인
	@Override
	public boolean isRejected(Long userId) {
		return redisTemplate.hasKey(REJECTED_PREFIX + userId);
	}
}
