package com.loopers.support.common.outbox.application.util;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * Outbox payload JSON 직렬화 유틸리티
 */

@Component
@RequiredArgsConstructor
public class JsonSerializer {

	// jackson
	private final ObjectMapper objectMapper;


	// 객체를 JSON 문자열로 변환
	public String toJson(Object object) {
		try {
			return objectMapper.writeValueAsString(object);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("JSON 직렬화 실패", e);
		}
	}

}
