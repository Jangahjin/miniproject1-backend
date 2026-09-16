package com.pharmaprice.auth.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.UserRole;

/** GET /api/v1/auth/me 응답 (docs/API.md §2). 비밀번호 해시는 절대 포함하지 않는다. */
public record MeResponse(
		long id, String email, String nickname, UserRole role, int reportCount, OffsetDateTime createdAt) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public static MeResponse from(AppUser user) {
		return new MeResponse(
				user.getId(), user.getEmail(), user.getNickname(), user.getRole(), user.getReportCount(),
				user.getCreatedAt().atZone(KST).toOffsetDateTime());
	}
}
