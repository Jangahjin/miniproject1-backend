package com.pharmaprice.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.pharmaprice.AbstractIntegrationTest;

/** docs/ROADMAP.md T-24 완료 판정. docs/API.md §2 5개 엔드포인트를 흐름 그대로 검증한다. */
@AutoConfigureMockMvc
class AuthControllerTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void 가입_로그인_내정보_로그아웃_흐름이_끝까지_동작한다() throws Exception {
		String email = "flow-" + System.nanoTime() + "@example.com";

		String signupResponse = mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"Password123!","nickname":"플로우"}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.nickname").value("플로우"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.createdAt").exists())
				.andReturn().getResponse().getContentAsString();
		assertThat(signupResponse).doesNotContain("passwordHash").doesNotContain("password_hash");

		String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"Password123!"}
								""".formatted(email)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").exists())
				.andExpect(jsonPath("$.refreshToken").exists())
				.andExpect(jsonPath("$.expiresIn").value(1800))
				.andExpect(jsonPath("$.user.nickname").value("플로우"))
				.andExpect(jsonPath("$.user.role").value("USER"))
				.andReturn().getResponse().getContentAsString();
		assertThat(loginResponse).doesNotContain("passwordHash");

		String accessToken = JsonPath.read(loginResponse, "$.accessToken");
		String refreshToken = JsonPath.read(loginResponse, "$.refreshToken");

		String meResponse = mockMvc.perform(get("/api/v1/auth/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.reportCount").value(0))
				.andReturn().getResponse().getContentAsString();
		assertThat(meResponse).doesNotContain("passwordHash");

		String logoutBody = "{\"refreshToken\":\"" + refreshToken + "\"}";
		mockMvc.perform(post("/api/v1/auth/logout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(logoutBody))
				.andExpect(status().isNoContent());

		// 로그아웃은 멱등해야 한다 — 두 번째 호출도 204
		mockMvc.perform(post("/api/v1/auth/logout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(logoutBody))
				.andExpect(status().isNoContent());

		// revoke된 refresh 토큰으로 갱신 시도 -> 401
		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(logoutBody))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void 중복_이메일로_가입하면_409를_반환한다() throws Exception {
		String email = "dup-" + System.nanoTime() + "@example.com";
		String body = "{\"email\":\"" + email + "\",\"password\":\"Password123!\",\"nickname\":\"중복\"}";

		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict());
	}

	@Test
	void 잘못된_이메일_형식으로_가입하면_400_VALIDATION_FAILED를_반환한다() throws Exception {
		String body = "{\"email\":\"not-an-email\",\"password\":\"Password123!\",\"nickname\":\"테스터\"}";

		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
	}

	@Test
	void 시드_admin_계정으로_로그인하면_role이_ADMIN이다() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"admin@example.com\",\"password\":\"Admin1234!\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.role").value("ADMIN"));
	}

	@Test
	void 잘못된_비밀번호로_로그인하면_401과_UNAUTHENTICATED를_반환한다() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"admin@example.com\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}
}
