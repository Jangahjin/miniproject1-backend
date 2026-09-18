package com.pharmaprice.auth.exception;

/** 상태 코드/응답 포맷은 GlobalExceptionHandler가 결정한다 (docs/ROADMAP.md T-35). */
public class EmailAlreadyExistsException extends RuntimeException {

	public EmailAlreadyExistsException(String email) {
		super("이미 가입된 이메일입니다: " + email);
	}
}
