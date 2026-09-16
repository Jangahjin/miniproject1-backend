package com.pharmaprice.auth.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code Authorization: Bearer} 헤더의 access 토큰을 검증해 인증 정보를 채운다
 * (docs/ROADMAP.md T-23). 토큰이 없거나 유효하지 않아도 여기서 401을 내리지
 * 않는다 — 인증되지 않은 채로 흘려보내면 이후 authorizeHttpRequests가
 * permitAll이 아닌 경로에서 걸러내고, 그 결과는 JwtAuthenticationEntryPoint가
 * API.md §1.2 포맷으로 응답한다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider jwtTokenProvider;

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
		this.jwtTokenProvider = jwtTokenProvider;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			try {
				JwtTokenProvider.AccessTokenClaims claims =
						jwtTokenProvider.parseAccessToken(header.substring(BEARER_PREFIX.length()));
				var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name()));
				var authentication =
						new UsernamePasswordAuthenticationToken(claims.userId(), null, authorities);
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} catch (JwtException | IllegalArgumentException e) {
				SecurityContextHolder.clearContext();
			}
		}
		filterChain.doFilter(request, response);
	}
}
