package com.pharmaprice.report.exception;

/** 상태 코드/응답 포맷은 GlobalExceptionHandler가 결정한다 (docs/ROADMAP.md T-35). */
public class FileTooLargeException extends RuntimeException {

	public FileTooLargeException() {
		super("파일 크기는 5MB를 초과할 수 없습니다.");
	}
}
