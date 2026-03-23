package com.loopers.payment.payment.domain.model.enums;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * 카드 타입
 * - SAMSUNG: 삼성카드
 * - KB: KB국민카드
 * - HYUNDAI: 현대카드
 */
public enum CardType {

	SAMSUNG,
	KB,
	HYUNDAI;


	// 이름 → CardType 매핑 캐시
	private static final Map<String, CardType> NAME_MAP = Stream.of(values())
		.collect(Collectors.toMap(Enum::name, v -> v));


	/**
	 * 도메인 로직
	 * 1. 문자열로부터 CardType 변환
	 */

	// 1. 문자열로부터 CardType 변환
	public static CardType from(String value) {
		if (value == null) {
			throw new CoreException(ErrorType.INVALID_CARD_TYPE);
		}
		CardType cardType = NAME_MAP.get(value.trim().toUpperCase(Locale.ROOT));
		if (cardType == null) {
			throw new CoreException(ErrorType.INVALID_CARD_TYPE);
		}
		return cardType;
	}

}
