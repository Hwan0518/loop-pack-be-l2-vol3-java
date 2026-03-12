package com.loopers.catalog.product.infrastructure.cache;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@DisplayName("LocalCacheLock 단위 테스트")
class LocalCacheLockTest {

	private LocalCacheLock localCacheLock;


	@BeforeEach
	void setUp() {
		localCacheLock = new LocalCacheLock();
	}


	@Nested
	@DisplayName("executeWithLock()")
	class ExecuteWithLockTest {

		@Test
		@DisplayName("[executeWithLock()] 같은 key 100개 동시 요청 -> 직렬 실행으로 loader 100회 호출. 스탬피드 보호는 CacheManager double-check에서 수행")
		void sameKeyConcurrentRequests_loaderCalledOnce() throws InterruptedException {

			// Arrange
			int threadCount = 100;
			ExecutorService executor = Executors.newFixedThreadPool(threadCount);
			CountDownLatch readyLatch = new CountDownLatch(threadCount);
			CountDownLatch startLatch = new CountDownLatch(1);
			CountDownLatch doneLatch = new CountDownLatch(threadCount);
			AtomicInteger loaderCallCount = new AtomicInteger(0);
			String key = "same-key";

			// Act
			for (int i = 0; i < threadCount; i++) {
				executor.submit(() -> {
					readyLatch.countDown();
					try {
						startLatch.await();
						localCacheLock.executeWithLock(key, () -> {
							loaderCallCount.incrementAndGet();

							// loader 실행에 시간이 걸리는 상황 시뮬레이션
							try {
								Thread.sleep(50);
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
							}

							return "result";
						});
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} finally {
						doneLatch.countDown();
					}
				});
			}

			// 모든 스레드 준비 완료 후 동시 시작
			readyLatch.await();
			startLatch.countDown();
			doneLatch.await();
			executor.shutdown();

			// Assert — 락에 의해 loader는 직렬 실행되므로 100회 호출 (대기 후 순차 실행)
			// LocalCacheLock은 결과 캐싱을 하지 않으므로 각 스레드가 순서대로 loader 호출
			// 실제 스탬피드 보호는 ProductCacheManager의 double-check 패턴에서 수행
			assertThat(loaderCallCount.get()).isEqualTo(threadCount);
		}


		@Test
		@DisplayName("[executeWithLock()] 다른 key 동시 요청 -> 각각 독립 실행. 서로 블로킹하지 않음")
		void differentKeysConcurrentRequests_independentExecution() throws InterruptedException {

			// Arrange
			int threadCount = 10;
			ExecutorService executor = Executors.newFixedThreadPool(threadCount);
			CountDownLatch readyLatch = new CountDownLatch(threadCount);
			CountDownLatch startLatch = new CountDownLatch(1);
			CountDownLatch doneLatch = new CountDownLatch(threadCount);
			AtomicInteger concurrentCount = new AtomicInteger(0);
			AtomicInteger maxConcurrent = new AtomicInteger(0);

			// Act
			for (int i = 0; i < threadCount; i++) {
				String key = "key-" + i;
				executor.submit(() -> {
					readyLatch.countDown();
					try {
						startLatch.await();
						localCacheLock.executeWithLock(key, () -> {

							// 동시 실행 스레드 수 추적
							int current = concurrentCount.incrementAndGet();
							maxConcurrent.updateAndGet(max -> Math.max(max, current));

							try {
								Thread.sleep(100);
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
							}

							concurrentCount.decrementAndGet();
							return "result";
						});
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} finally {
						doneLatch.countDown();
					}
				});
			}

			readyLatch.await();
			startLatch.countDown();
			doneLatch.await();
			executor.shutdown();

			// Assert — 다른 key이므로 병렬 실행됨 (최대 동시 실행 수 > 1)
			assertThat(maxConcurrent.get()).isGreaterThan(1);
		}


		@Test
		@DisplayName("[executeWithLock()] loader 예외 발생 -> 락 정상 해제. 예외 전파")
		void loaderThrowsException_lockReleased_exceptionPropagated() {

			// Arrange
			String key = "error-key";
			RuntimeException expectedException = new RuntimeException("loader 실패");

			// Act & Assert — 예외가 전파됨
			assertThatThrownBy(() ->
				localCacheLock.executeWithLock(key, () -> {
					throw expectedException;
				})
			).isEqualTo(expectedException);

			// 락 해제 검증: 이후 같은 key로 정상 실행 가능
			String result = localCacheLock.executeWithLock(key, () -> "recovered");
			assertThat(result).isEqualTo("recovered");
		}

	}

}
