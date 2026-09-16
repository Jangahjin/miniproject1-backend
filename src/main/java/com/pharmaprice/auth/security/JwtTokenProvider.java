package com.pharmaprice.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.UserRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT access 토큰 발급·검증과 refresh 토큰(원문/해시) 생성을 담당한다
 * (docs/ROADMAP.md T-23). refresh 토큰 자체는 서명된 JWT가 아니라 무작위
 * 문자열이다 — 검증은 서명이 아니라 DB의 해시 대조로 하기 때문에 JWT일
 * 필요가 없다.
 */
@Component
public class JwtTokenProvider {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final SecretKey key;
	private final Duration accessTokenTtl;
	private final Duration refreshTokenTtl;

	public JwtTokenProvider(
			@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes,
			@Value("${app.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
		this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
	}

	public String generateAccessToken(AppUser user) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(String.valueOf(user.getId()))
				.claim("role", user.getRole().name())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(accessTokenTtl)))
				.signWith(key, Jwts.SIG.HS256)
				.compact();
	}

	/** 서명·만료가 유효하지 않으면 {@link io.jsonwebtoken.JwtException} 을 던진다. */
	public AccessTokenClaims parseAccessToken(String token) {
		Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
		return new AccessTokenClaims(
				Long.parseLong(claims.getSubject()), UserRole.valueOf(claims.get("role", String.class)));
	}

	public String generateRefreshTokenValue() {
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public String hashToken(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
		}
	}

	public Duration getRefreshTokenTtl() {
		return refreshTokenTtl;
	}

	public record AccessTokenClaims(long userId, UserRole role) {
	}
}
