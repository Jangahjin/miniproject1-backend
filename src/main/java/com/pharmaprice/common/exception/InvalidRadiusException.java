package com.pharmaprice.common.exception;

/** 반경이 허용값(500/1000/2000/5000)을 벗어났을 때 (docs/API.md §1.3 INVALID_RADIUS). */
public class InvalidRadiusException extends RuntimeException {

	public InvalidRadiusException(int radiusM) {
		super("허용되지 않는 반경입니다: " + radiusM);
	}
}
