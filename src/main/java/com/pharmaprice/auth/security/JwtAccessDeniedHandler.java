package com.pharmaprice.auth.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.pharmaprice.common.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/** 인증은 됐지만 권한이 부족할 때(예: USER가 ADMIN 전용 API 호출) API.md §1.2 포맷의 403을 내려준다. */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

	private final ObjectMapper objectMapper;

	public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void handle(
			HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
			throws IOException {
		SecurityResponseWriter.write(
				response, objectMapper, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.name(), ErrorCode.FORBIDDEN.message());
	}
}
