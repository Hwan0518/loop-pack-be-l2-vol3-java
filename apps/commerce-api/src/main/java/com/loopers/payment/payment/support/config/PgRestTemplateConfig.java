package com.loopers.payment.payment.support.config;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;


/**
 * PG 전용 RestTemplate 설정
 * - connectTimeout: 1초
 * - readTimeout: 3초
 */
@Configuration
public class PgRestTemplateConfig {

	@Bean
	@Qualifier("pgRestTemplate")
	public RestTemplate pgRestTemplate() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(1));
		factory.setReadTimeout(Duration.ofSeconds(3));
		return new RestTemplate(factory);
	}

}
