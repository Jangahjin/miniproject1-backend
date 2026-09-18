package com.pharmaprice.drug.exception;

/** 상태 코드/응답 포맷은 GlobalExceptionHandler가 결정한다 (docs/ROADMAP.md T-35). */
public class DrugNotFoundException extends RuntimeException {

	public DrugNotFoundException(long drugId) {
		super("약을 찾을 수 없습니다: id=" + drugId);
	}
}
