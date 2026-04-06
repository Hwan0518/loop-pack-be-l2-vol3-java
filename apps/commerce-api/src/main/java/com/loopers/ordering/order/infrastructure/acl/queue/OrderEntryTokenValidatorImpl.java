package com.loopers.ordering.order.infrastructure.acl.queue;


import com.loopers.ordering.order.application.port.out.client.queue.OrderEntryTokenValidator;
import com.loopers.queue.waitingqueue.application.facade.EntryTokenCommandFacade;
import org.springframework.stereotype.Component;


/**
 * 입장 토큰 검증/락 ACL 구현체 (ordering → queue)
 * - queue BC의 EntryTokenCommandFacade에 위임 (Provider Facade 호출 규칙 준수)
 */
@Component
public class OrderEntryTokenValidatorImpl implements OrderEntryTokenValidator {

	// queue BC facade
	private final EntryTokenCommandFacade entryTokenCommandFacade;


	public OrderEntryTokenValidatorImpl(EntryTokenCommandFacade entryTokenCommandFacade) {
		this.entryTokenCommandFacade = entryTokenCommandFacade;
	}


	// 1. 입장 토큰 검증 + 주문 처리 락 획득
	@Override
	public String validateAndLock(Long userId, String entryToken) {
		return entryTokenCommandFacade.validateAndLock(userId, entryToken);
	}


	// 2. 주문 완료 후 정리
	@Override
	public void completeOrder(Long userId, String lockValue) {
		entryTokenCommandFacade.completeOrder(userId, lockValue);
	}


	// 3. 주문 실패 후 정리
	@Override
	public void releaseOrderLock(Long userId, String lockValue) {
		entryTokenCommandFacade.releaseOrderLock(userId, lockValue);
	}
}
