package com.loopers.ordering.order.infrastructure.acl.queue;


import com.loopers.ordering.order.application.port.out.client.queue.OrderEntryTokenValidator;
import com.loopers.queue.waitingqueue.application.facade.EntryTokenCommandFacade;
import org.springframework.stereotype.Component;


/**
 * 입장 토큰 소비 ACL 구현체 (ordering → queue)
 * - queue BC의 EntryTokenCommandFacade에 위임 (Provider Facade 호출 규칙 준수)
 */
@Component
public class OrderEntryTokenValidatorImpl implements OrderEntryTokenValidator {

	// queue BC facade
	private final EntryTokenCommandFacade entryTokenCommandFacade;


	public OrderEntryTokenValidatorImpl(EntryTokenCommandFacade entryTokenCommandFacade) {
		this.entryTokenCommandFacade = entryTokenCommandFacade;
	}


	// 1. 입장 토큰 원자적 소비
	@Override
	public void consume(Long userId, String entryToken) {
		entryTokenCommandFacade.consumeEntryToken(userId, entryToken);
	}
}
