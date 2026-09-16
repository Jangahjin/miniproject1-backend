package com.pharmaprice.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pharmaprice.auth.dto.LoginRequest;
import com.pharmaprice.auth.dto.LoginResponse;
import com.pharmaprice.auth.dto.LogoutRequest;
import com.pharmaprice.auth.dto.MeResponse;
import com.pharmaprice.auth.dto.RefreshRequest;
import com.pharmaprice.auth.dto.SignupRequest;
import com.pharmaprice.auth.dto.SignupResponse;
import com.pharmaprice.auth.service.AuthService;

import jakarta.validation.Valid;

/** 가입·로그인·갱신·로그아웃·내정보 (docs/API.md §2, docs/ROADMAP.md T-24). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
		return authService.signup(request);
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@PostMapping("/refresh")
	public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return authService.refresh(request);
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@Valid @RequestBody LogoutRequest request) {
		authService.logout(request.refreshToken());
	}

	@GetMapping("/me")
	public MeResponse me(@AuthenticationPrincipal Long userId) {
		return authService.me(userId);
	}
}
