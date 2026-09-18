package com.pharmaprice.report.exception;

/** 상태 코드/응답 포맷은 GlobalExceptionHandler가 결정한다 (docs/ROADMAP.md T-35). */
public class UnsupportedFileTypeException extends RuntimeException {

	public UnsupportedFileTypeException() {
		super("jpg, png, webp 형식의 이미지만 업로드할 수 있습니다.");
	}
}
