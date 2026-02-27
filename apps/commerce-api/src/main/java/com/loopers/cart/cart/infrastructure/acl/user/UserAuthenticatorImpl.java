package com.loopers.cart.cart.infrastructure.acl.user;


import com.loopers.cart.cart.application.port.out.client.user.UserAuthenticator;
import com.loopers.user.user.application.facade.UserQueryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * ACL (Anti-Corruption Layer) - 장바구니 BC → 사용자 BC 통합 구현체 (사용자 인증)
 * 장바구니 요청 시 사용자 인증을 수행하는 역할 (Provider Facade에 위임)
 */
@Component("cartUserAuthenticator")
@RequiredArgsConstructor
public class UserAuthenticatorImpl implements UserAuthenticator {

	// facade: 사용자 조회 파사드 (user BC)
	private final UserQueryFacade userQueryFacade;


	/**
	 * 사용자 인증 후 사용자 ID 반환
	 * 1. Provider Facade를 통해 사용자 인증 후 ID 반환
	 */

	// 1. 사용자 인증
	@Override
	public Long authenticate(String loginId, String password) {

		// facade: 사용자 인증 후 ID 반환 (CoreException 발생 가능)
		return userQueryFacade.authenticateAndGetUserId(loginId, password);
	}

}
