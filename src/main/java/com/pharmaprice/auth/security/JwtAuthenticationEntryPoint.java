package com.pharmaprice.auth.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.pharmaprice.common.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/** 토큰 없음/만료/서명 불일치 시 API.md §1.2 포맷의 401을 내려준다 (docs/ROADMAP.md T-23). */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(
			HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
			throws IOException {
		SecurityResponseWriter.write(
				response, objectMapper, HttpStatus.UNAUTHORIZED,
				ErrorCode.UNAUTHENTICATED.name(), ErrorCode.UNAUTHENTICATED.message());
	}
}
