package com.pharmaprice.auth.dto;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.UserRole;

/** POST /api/v1/auth/login, /api/v1/auth/refresh 공통 응답 (docs/API.md §2). */
public record LoginResponse(String accessToken, String refreshToken, long expiresIn, UserSummary user) {

	public record UserSummary(long id, String nickname, UserRole role) {

		public static UserSummary from(AppUser user) {
			return new UserSummary(user.getId(), user.getNickname(), user.getRole());
		}
	}
}
