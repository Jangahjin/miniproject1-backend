package com.pharmaprice.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/auth/logout 요청 (docs/API.md §2). */
public record LogoutRequest(@NotBlank String refreshToken) {
}
