package com.pharmaprice.pharmacy.exception;

/** 상태 코드/응답 포맷은 GlobalExceptionHandler가 결정한다 (docs/ROADMAP.md T-35). */
public class PharmacyNotFoundException extends RuntimeException {

	public PharmacyNotFoundException(long pharmacyId) {
		super("약국을 찾을 수 없습니다: id=" + pharmacyId);
	}
}
