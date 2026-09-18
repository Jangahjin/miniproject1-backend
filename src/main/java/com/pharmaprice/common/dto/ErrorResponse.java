package com.pharmaprice.common.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.slf4j.MDC;

import com.pharmaprice.common.web.TraceIdFilter;

/**
 * 에러 응답 포맷 (docs/API.md §1.2). {@link GlobalExceptionHandler}(예외 발생 시)와
 * {@code SecurityResponseWriter}(401/403, T-23)가 공유한다.
 */
public record ErrorResponse(
		String code, String message, List<FieldError> fieldErrors, String traceId, OffsetDateTime timestamp) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public record FieldError(String field, String reason) {
	}

	public static ErrorResponse of(String code, String message) {
		return of(code, message, null);
	}

	public static ErrorResponse of(String code, String message, List<FieldError> fieldErrors) {
		return new ErrorResponse(code, message, fieldErrors, currentTraceId(), OffsetDateTime.now(KST));
	}

	// TraceIdFilter가 요청마다 MDC에 심어둔 traceId와 응답의 traceId를 동일하게
	// 맞춰 "응답의 traceId로 로그를 검색"할 수 있게 한다. 필터 없이(단위 테스트 등)
	// 호출되면 MDC가 비어있으므로 그때만 새로 생성한다.
	private static String currentTraceId() {
		String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
		return traceId != null ? traceId : UUID.randomUUID().toString().substring(0, 8);
	}
}
