package com.loopers.user.support.config;

import com.loopers.user.application.repository.UserQueryRepository;
import com.loopers.user.domain.service.LoginIdDuplicateValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

	@Bean
	public LoginIdDuplicateValidator loginIdDuplicateValidator(UserQueryRepository userQueryRepository) {
		return new LoginIdDuplicateValidator(userQueryRepository::existsByLoginId);
	}
}
