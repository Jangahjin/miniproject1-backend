package com.pharmaprice.auth.service;

import java.time.Instant;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.RefreshToken;
import com.pharmaprice.auth.dto.LoginRequest;
import com.pharmaprice.auth.dto.LoginResponse;
import com.pharmaprice.auth.dto.MeResponse;
import com.pharmaprice.auth.dto.RefreshRequest;
import com.pharmaprice.auth.dto.SignupRequest;
import com.pharmaprice.auth.dto.SignupResponse;
import com.pharmaprice.auth.exception.EmailAlreadyExistsException;
import com.pharmaprice.auth.repository.AppUserRepository;
import com.pharmaprice.auth.repository.RefreshTokenRepository;
import com.pharmaprice.auth.security.JwtTokenProvider;

/**
 * 가입·로그인·토큰 회전·로그아웃·내정보 (docs/ROADMAP.md T-24, docs/API.md §2).
 * 로그인/갱신 실패는 {@link BadCredentialsException}(AuthenticationException의
 * 일종)을 던진다 — Spring Security의 ExceptionTranslationFilter가 이를 잡아
 * T-23에서 만든 JwtAuthenticationEntryPoint로 넘기므로, 전역 예외 처리(T-35) 없이도
 * API.md §1.2 포맷의 401을 그대로 재사용할 수 있다.
 */
@Service
public class AuthService {

	private final AppUserRepository appUserRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;

	public AuthService(
			AppUserRepository appUserRepository,
			RefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenProvider jwtTokenProvider) {
		this.appUserRepository = appUserRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
	}

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		if (appUserRepository.findByEmail(request.email()).isPresent()) {
			throw new EmailAlreadyExistsException(request.email());
		}
		AppUser user = AppUser.builder()
				.email(request.email())
				.passwordHash(passwordEncoder.encode(request.password()))
				.nickname(request.nickname())
				.build();
		return SignupResponse.from(appUserRepository.save(user));
	}

	@Transactional
	public LoginResponse login(LoginRequest request) {
		AppUser user = appUserRepository.findByEmail(request.email())
				.orElseThrow(AuthService::invalidCredentials);
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw invalidCredentials();
		}
		return issueTokens(user);
	}

	@Transactional
	public LoginResponse refresh(RefreshRequest request) {
		RefreshToken current = refreshTokenRepository
				.findByTokenHash(jwtTokenProvider.hashToken(request.refreshToken()))
				.filter(RefreshToken::isValid)
				.orElseThrow(AuthService::invalidCredentials);
		current.revoke();
		return issueTokens(current.getUser());
	}

	/** 이미 revoke됐거나 존재하지 않는 토큰이어도 조용히 넘어간다 — 로그아웃은 멱등해야 한다. */
	@Transactional
	public void logout(String rawRefreshToken) {
		refreshTokenRepository.findByTokenHash(jwtTokenProvider.hashToken(rawRefreshToken))
				.filter(token -> token.getRevokedAt() == null)
				.ifPresent(RefreshToken::revoke);
	}

	@Transactional(readOnly = true)
	public MeResponse me(long userId) {
		return appUserRepository.findById(userId).map(MeResponse::from).orElseThrow(AuthService::invalidCredentials);
	}

	private LoginResponse issueTokens(AppUser user) {
		String accessToken = jwtTokenProvider.generateAccessToken(user);
		String rawRefreshToken = jwtTokenProvider.generateRefreshTokenValue();

		RefreshToken refreshToken = RefreshToken.builder()
				.user(user)
				.tokenHash(jwtTokenProvider.hashToken(rawRefreshToken))
				.expiresAt(Instant.now().plus(jwtTokenProvider.getRefreshTokenTtl()))
				.build();
		refreshTokenRepository.save(refreshToken);

		return new LoginResponse(
				accessToken, rawRefreshToken, jwtTokenProvider.getAccessTokenTtl().toSeconds(),
				LoginResponse.UserSummary.from(user));
	}

	private static BadCredentialsException invalidCredentials() {
		return new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.");
	}
}
