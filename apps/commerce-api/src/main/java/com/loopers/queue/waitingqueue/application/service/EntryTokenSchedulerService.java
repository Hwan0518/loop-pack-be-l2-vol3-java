package com.loopers.queue.waitingqueue.application.service;


import com.loopers.queue.waitingqueue.application.port.out.WaitingQueueRedisPort;
import com.loopers.queue.waitingqueue.support.config.QueueTokenProperties;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


/**
 * 입장 토큰 발급 스케줄러
 * - 100ms마다 실행, ShedLock으로 단일 리더 보장
 * - Lua script로 ZPOPMIN + SET EX 원자적 처리
 * - 1회 배치: 18명 (175 TPS 기준)
 */
@Service
public class EntryTokenSchedulerService {

	private static final Logger log = LoggerFactory.getLogger(EntryTokenSchedulerService.class);
	private static final int BATCH_SIZE = 18;

	// port
	private final WaitingQueueRedisPort waitingQueueRedisPort;

	// config
	private final QueueTokenProperties queueTokenProperties;


	public EntryTokenSchedulerService(
		WaitingQueueRedisPort waitingQueueRedisPort,
		QueueTokenProperties queueTokenProperties
	) {
		this.waitingQueueRedisPort = waitingQueueRedisPort;
		this.queueTokenProperties = queueTokenProperties;
	}


	// 1. 입장 토큰 발급 (100ms 주기, ShedLock 단일 리더)
	@Scheduled(fixedRate = 100)
	@SchedulerLock(name = "queue-token-issuer", lockAtLeastFor = "PT0.05S", lockAtMostFor = "PT5S")
	public void issueEntryTokens() {
		List<String> issuedUserIds = waitingQueueRedisPort.issueEntryTokens(
			BATCH_SIZE, queueTokenProperties.tokenTtlSeconds()
		);

		if (!issuedUserIds.isEmpty()) {
			log.debug("입장 토큰 발급: {}명", issuedUserIds.size());
		}
	}
}
