package com.pharmaprice.common.exception;

import org.springframework.http.HttpStatus;

/**
 * docs/API.md §1.3 에러 코드 표와 1:1 대응 (enum 상수명이 곧 응답의 {@code code}
 * 문자열이다 — {@link #name()}). UNAUTHENTICATED/FORBIDDEN은 Spring Security 필터
 * 체인(T-23, SecurityResponseWriter)에서 이 enum을 참조만 하고, 실제 예외 처리는
 * {@link GlobalExceptionHandler}가 담당한다.
 *
 * <p>FILE_NOT_FOUND, DATA_CONFLICT는 문서에는 없지만 실제로 필요한 방어용 코드다
 * (업로드 파일 조회 실패, DB 유니크 제약 충돌 방어).
 */
public enum ErrorCode {

	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해주세요."),
	INVALID_COORDINATE(HttpStatus.BAD_REQUEST, "대한민국 범위를 벗어난 좌표입니다."),
	INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "구매일이 올바르지 않습니다."),
	INVALID_RADIUS(HttpStatus.BAD_REQUEST, "허용되지 않는 반경입니다."),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	PHARMACY_NOT_FOUND(HttpStatus.NOT_FOUND, "약국을 찾을 수 없습니다."),
	DRUG_NOT_FOUND(HttpStatus.NOT_FOUND, "약을 찾을 수 없습니다."),
	REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "제보를 찾을 수 없습니다."),
	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
	DUPLICATE_REPORT(HttpStatus.CONFLICT, "오늘 이미 이 약국의 해당 약품 가격을 제보하셨습니다."),
	FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 크기는 5MB를 초과할 수 없습니다."),
	UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "jpg, png, webp 형식의 이미지만 업로드할 수 있습니다."),
	DRUG_NOT_OTC(HttpStatus.UNPROCESSABLE_ENTITY, "일반의약품이 아니라 제보할 수 없습니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),

	// --- 문서(§1.3)에는 없지만 실제로 필요한 방어용 코드 ---
	FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
	DATA_CONFLICT(HttpStatus.CONFLICT, "요청이 기존 데이터와 충돌합니다.");

	private final HttpStatus httpStatus;
	private final String message;

	ErrorCode(HttpStatus httpStatus, String message) {
		this.httpStatus = httpStatus;
		this.message = message;
	}

	public HttpStatus httpStatus() {
		return httpStatus;
	}

	public String message() {
		return message;
	}
}
