package com.pharmaprice.common.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 실제 인증/인가(JWT 기반 로그인 판별)는 T-23~T-25에서 구현한다. 그 전까지는
 * Swagger·Actuator와, API.md에 🔓(비로그인 허용)로 표시된 공개 조회 API를
 * 엔드포인트가 생길 때마다 하나씩 열어둔다.
 */
@Configuration
public class SecurityConfig {

	@Value("${app.cors.allowed-origins}")
	private String allowedOrigins;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(
								"/swagger-ui/**", "/swagger-ui.html",
								"/v3/api-docs/**",
								"/actuator/**",
								"/error") // 없으면 컨트롤러 500이 /error 포워드에서 401/403으로 둔갑해 원인 파악이 어려워진다.
						.permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/drugs/**", "/api/v1/regions/**", "/api/v1/search/**")
						.permitAll()
						.anyRequest().authenticated());
		return http.build();
	}

	/**
	 * permitAll은 인증만 우회할 뿐 CORS 프리플라이트와는 무관하다. 프론트(3000)와
	 * 백엔드(8080)가 서로 다른 오리진이라, 이게 없으면 브라우저가 응답을 막는다.
	 */
	private CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}
}
