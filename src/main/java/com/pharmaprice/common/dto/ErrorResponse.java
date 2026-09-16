package com.pharmaprice.common.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 에러 응답 포맷 (docs/API.md §1.2). 전역 예외 처리(docs/ROADMAP.md T-35)가 붙기
 * 전까지는 인증/인가 실패(T-23)에서만 이 포맷을 직접 사용한다.
 */
public record ErrorResponse(
		String code, String message, List<FieldError> fieldErrors, String traceId, OffsetDateTime timestamp) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public record FieldError(String field, String reason) {
	}

	public static ErrorResponse of(String code, String message) {
		return new ErrorResponse(
				code, message, null, UUID.randomUUID().toString().substring(0, 8), OffsetDateTime.now(KST));
	}
}
