package com.pharmaprice.auth.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.pharmaprice.common.dto.ErrorResponse;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/** {@link JwtAuthenticationEntryPoint} 와 {@link JwtAccessDeniedHandler} 가 공유하는 응답 포맷터. */
final class SecurityResponseWriter {

	private SecurityResponseWriter() {
	}

	static void write(
			HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String code, String message)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code, message));
	}
}
