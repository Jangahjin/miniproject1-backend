package com.pharmaprice.report.exception;

/**
 * 상태 코드/응답 포맷은 GlobalExceptionHandler가 결정한다 (docs/ROADMAP.md T-35).
 * {@link #getMessage()}에는 pharmacyId/drugId가 포함돼 있어 응답 메시지로는 쓰지
 * 않는다 — GlobalExceptionHandler가 별도의 정제된 메시지를 쓴다.
 */
public class DuplicateReportException extends RuntimeException {

	public DuplicateReportException(long pharmacyId, long drugId) {
		super("오늘 이미 제보한 약국·약품 조합입니다: pharmacyId=" + pharmacyId + ", drugId=" + drugId);
	}
}
