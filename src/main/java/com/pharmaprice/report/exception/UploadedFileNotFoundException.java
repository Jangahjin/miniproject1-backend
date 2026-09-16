package com.pharmaprice.report.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 전역 예외 처리(docs/ROADMAP.md T-35)가 붙기 전까지는 상태 코드만 맞춰준다.
 * 응답 바디를 docs/API.md §1.2 형식(code/message/traceId 등)으로 맞추는 건 T-35에서 한다.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class UploadedFileNotFoundException extends RuntimeException {

	public UploadedFileNotFoundException(long fileId) {
		super("파일을 찾을 수 없습니다: id=" + fileId);
	}
}
