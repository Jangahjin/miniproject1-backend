package com.pharmaprice.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 실제 인증/인가(JWT 기반 로그인 판별)는 T-23~T-25에서 구현한다. 그 전까지는
 * Swagger·Actuator와, API.md에 🔓(비로그인 허용)로 표시된 공개 조회 API를
 * 엔드포인트가 생길 때마다 하나씩 열어둔다.
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
						.requestMatchers(HttpMethod.GET, "/api/v1/drugs/**")
						.permitAll()
						.anyRequest().authenticated());
		return http.build();
	}
}
