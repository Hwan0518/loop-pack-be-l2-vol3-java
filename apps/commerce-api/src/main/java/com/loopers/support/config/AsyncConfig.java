package com.loopers.support.config;


import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;


/**
 * 비동기 이벤트 처리를 위한 스레드 풀 설정
 * - @TransactionalEventListener + @Async 조합에서 사용
 */

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

	// 1. 이벤트 비동기 처리용 스레드 풀
	@Override
	public Executor getAsyncExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(5);
		executor.setMaxPoolSize(20);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("event-async-");
		executor.initialize();
		return executor;
	}


	// 2. 비동기 예외 핸들러
	@Override
	public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return new SimpleAsyncUncaughtExceptionHandler();
	}

}
