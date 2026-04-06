package com.loopers.queue.waitingqueue.support.config;


import com.loopers.config.redis.RedisConfig;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;


/**
 * ShedLock + 스케줄링 설정
 * - 분산 환경에서 스케줄러 단일 리더 보장
 * - Redis 기반 락 제공자
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
public class ShedLockConfig {

	@Bean
	public LockProvider lockProvider(
		@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate
	) {
		return new RedisLockProvider(redisTemplate.getConnectionFactory());
	}
}
