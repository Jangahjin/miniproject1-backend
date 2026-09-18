package com.pharmaprice.common.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 traceId를 MDC에 심어 응답 바디(ErrorResponse)와 로그가 같은 값을
 * 공유하게 한다 (docs/ROADMAP.md T-35). WebConfig에서 Spring Security 필터
 * 체인보다도 먼저 실행되도록 등록해야 401/403 응답도 traceId를 갖는다.
 */
public class TraceIdFilter extends OncePerRequestFilter {

	public static final String TRACE_ID_KEY = "traceId";

	private static final int TRACE_ID_LENGTH = 8;

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		MDC.put(TRACE_ID_KEY, UUID.randomUUID().toString().substring(0, TRACE_ID_LENGTH));
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(TRACE_ID_KEY);
		}
	}
}
