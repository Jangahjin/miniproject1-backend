package com.pharmaprice.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /api/v1/auth/signup 요청 (docs/API.md §2). */
public record SignupRequest(
		@NotBlank(message = "{auth.email.required}")
		@Email(message = "{auth.email.invalid}")
		@Size(max = 255, message = "{auth.email.size}")
		String email,
		@NotBlank(message = "{auth.password.required}")
		@Size(min = 8, max = 64, message = "{auth.password.size}")
		@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "{auth.password.pattern}")
		String password,
		@NotBlank(message = "{auth.nickname.required}")
		@Size(min = 2, max = 30, message = "{auth.nickname.size}")
		String nickname) {
}
