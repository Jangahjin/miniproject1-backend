package com.pharmaprice.report.exception;

/** 상태 코드/응답 포맷은 GlobalExceptionHandler가 결정한다 (docs/ROADMAP.md T-35). */
public class UploadedFileNotFoundException extends RuntimeException {

	public UploadedFileNotFoundException(long fileId) {
		super("파일을 찾을 수 없습니다: id=" + fileId);
	}
}
