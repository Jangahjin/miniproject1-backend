package com.pharmaprice.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/auth/login 요청 (docs/API.md §2). */
public record LoginRequest(
		@NotBlank(message = "{auth.email.required}") String email,
		@NotBlank(message = "{auth.password.required}") String password) {
}
