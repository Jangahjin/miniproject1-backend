package com.pharmaprice.auth.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.UserRole;
import com.pharmaprice.auth.repository.AppUserRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * docs/ROADMAP.md T-23 완료 판정. 아직 /api/v1/price-reports, /api/v1/admin/** 가
 * 구현되지 않았지만, Spring Security 필터 체인은 컨트롤러 존재 여부와 무관하게
 * URL·메서드만으로 인증/인가를 먼저 판단하므로 그대로 검증할 수 있다.
 */
@AutoConfigureMockMvc
class JwtSecurityTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	AppUserRepository appUserRepository;
	@Autowired
	JwtTokenProvider jwtTokenProvider;
	@Autowired
	JdbcTemplate jdbcTemplate;

	@Value("${app.jwt.secret}")
	String jwtSecret;

	@Test
	void 토큰_없이_보호된_API를_호출하면_401과_UNAUTHENTICATED를_반환한다() throws Exception {
		mockMvc.perform(post("/api/v1/price-reports")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void USER_토큰으로_관리자_API를_호출하면_403과_FORBIDDEN을_반환한다() throws Exception {
		AppUser user = appUserRepository.save(AppUser.builder()
				.email("jwt-test-" + System.nanoTime() + "@example.com")
				.passwordHash("dummy-hash")
				.nickname("테스트유저")
				.role(UserRole.USER)
				.build());
		String token = jwtTokenProvider.generateAccessToken(user);

		mockMvc.perform(get("/api/v1/admin/stats/overview")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void 만료된_토큰으로_호출하면_401을_반환한다() throws Exception {
		SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
		String expiredToken = Jwts.builder()
				.subject("1")
				.claim("role", "USER")
				.issuedAt(Date.from(Instant.now().minus(Duration.ofHours(2))))
				.expiration(Date.from(Instant.now().minus(Duration.ofHours(1))))
				.signWith(key, Jwts.SIG.HS256)
				.compact();

		mockMvc.perform(post("/api/v1/price-reports")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void 검색_API는_토큰_없이도_200이다() throws Exception {
		Long drugId = jdbcTemplate.queryForObject("SELECT id FROM drug ORDER BY id LIMIT 1", Long.class);

		mockMvc.perform(get("/api/v1/search")
						.param("drugId", String.valueOf(drugId))
						.param("lat", "37.5665")
						.param("lng", "126.978"))
				.andExpect(status().isOk());
	}
}
