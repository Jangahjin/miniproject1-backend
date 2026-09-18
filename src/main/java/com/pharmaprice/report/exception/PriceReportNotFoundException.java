package com.pharmaprice.report.exception;

/** 상태 코드/응답 포맷은 GlobalExceptionHandler가 결정한다 (docs/ROADMAP.md T-35). */
public class PriceReportNotFoundException extends RuntimeException {

	public PriceReportNotFoundException(long reportId) {
		super("제보를 찾을 수 없습니다: id=" + reportId);
	}
}
