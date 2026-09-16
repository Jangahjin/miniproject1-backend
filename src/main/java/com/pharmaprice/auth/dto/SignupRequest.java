package com.pharmaprice.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /api/v1/auth/signup 요청 (docs/API.md §2). */
public record SignupRequest(
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Size(min = 8, max = 64) @Pattern(
				regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
				message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다.")
		String password,
		@NotBlank @Size(min = 2, max = 30) String nickname) {
}
