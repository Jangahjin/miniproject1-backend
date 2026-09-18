package com.pharmaprice.common.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.pharmaprice.common.web.TraceIdFilter;

@Configuration
public class WebConfig {

	// Spring Security의 FilterChainProxy는 매우 이른 순서(-100)에 등록된다. traceId가
	// 401/403 응답에도 실리려면 그보다 먼저 실행돼야 하므로 HIGHEST_PRECEDENCE로 둔다.
	@Bean
	FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
		FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(new TraceIdFilter());
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}
}
