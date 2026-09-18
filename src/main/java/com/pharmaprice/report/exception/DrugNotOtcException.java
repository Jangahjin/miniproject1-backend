package com.pharmaprice.report.exception;

/** 상태 코드/응답 포맷은 GlobalExceptionHandler가 결정한다 (docs/ROADMAP.md T-35). */
public class DrugNotOtcException extends RuntimeException {

	public DrugNotOtcException(long drugId) {
		super("일반의약품이 아니라 제보할 수 없습니다: drugId=" + drugId);
	}
}
