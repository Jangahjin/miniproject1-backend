package com.pharmaprice.auth.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.UserRole;

/** POST /api/v1/auth/signup 응답 (docs/API.md §2). 비밀번호 해시는 절대 포함하지 않는다. */
public record SignupResponse(long id, String email, String nickname, UserRole role, OffsetDateTime createdAt) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public static SignupResponse from(AppUser user) {
		return new SignupResponse(
				user.getId(), user.getEmail(), user.getNickname(), user.getRole(),
				user.getCreatedAt().atZone(KST).toOffsetDateTime());
	}
}
