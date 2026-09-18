package com.pharmaprice.report.exception;

/** 상태 코드/응답 포맷은 GlobalExceptionHandler가 결정한다 (docs/ROADMAP.md T-35). */
public class InvalidDateRangeException extends RuntimeException {

	public InvalidDateRangeException(String message) {
		super(message);
	}
}
