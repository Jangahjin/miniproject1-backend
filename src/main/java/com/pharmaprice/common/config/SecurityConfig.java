package com.pharmaprice.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 실제 인증/인가는 T-23~T-25에서 구현한다. 그 전까지는 Swagger와 Actuator만 열어둔다.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(
								"/swagger-ui/**", "/swagger-ui.html",
								"/v3/api-docs/**",
								"/actuator/**")
						.permitAll()
						.anyRequest().authenticated());
		return http.build();
	}
}
