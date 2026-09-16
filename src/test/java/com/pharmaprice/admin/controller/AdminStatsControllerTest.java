package com.pharmaprice.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.pharmaprice.AbstractIntegrationTest;

/** docs/ROADMAP.md T-31 완료 판정. */
@AutoConfigureMockMvc
class AdminStatsControllerTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	JdbcTemplate jdbcTemplate;

	String adminToken;
	String userToken;

	@BeforeEach
	void setUp() throws Exception {
		adminToken = login("admin@example.com", "Admin1234!");

		String email = "admin-test-user-" + System.nanoTime() + "@example.com";
		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"Password123!","nickname":"일반유저"}
								""".formatted(email)))
				.andExpect(status().isCreated());
		userToken = login(email, "Password123!");
	}

	private String login(String email, String password) throws Exception {
		String response = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.accessToken");
	}

	@Test
	void USER_토큰으로_4개_엔드포인트_전부_403이다() throws Exception {
		mockMvc.perform(get("/api/v1/admin/stats/overview").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/admin/stats/regions").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/admin/stats/drugs/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/admin/stats/price-gaps").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isForbidden());
	}

	@Test
	void overview는_totals와_최근7일_추이를_반환한다() throws Exception {
		mockMvc.perform(get("/api/v1/admin/stats/overview").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totals.pharmacyCount", greaterThan(0)))
				.andExpect(jsonPath("$.totals.drugCount", greaterThan(0)))
				.andExpect(jsonPath("$.totals.reportCount", greaterThan(0)))
				.andExpect(jsonPath("$.totals.userCount", greaterThan(0)))
				.andExpect(jsonPath("$.recentTrend.length()").value(7))
				.andExpect(jsonPath("$.coverageRate").exists());
	}

	@Test
	void regions는_표본_3건_미만_지역을_제외한다() throws Exception {
		String body = mockMvc.perform(get("/api/v1/admin/stats/regions")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		java.util.List<Integer> pharmacyCounts = JsonPath.read(body, "$.rows[*].pharmacyCount");
		assertThat(pharmacyCounts).allSatisfy(count -> assertThat(count).isGreaterThanOrEqualTo(3));
	}

	@Test
	void 약품_통계는_분포_지역별평균_전국통계를_반환한다() throws Exception {
		Long drugId = jdbcTemplate.queryForObject(
				"SELECT drug_id FROM pharmacy_drug_price_stat GROUP BY drug_id ORDER BY COUNT(*) DESC LIMIT 1",
				Long.class);

		mockMvc.perform(get("/api/v1/admin/stats/drugs/{drugId}", drugId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.drug.id").value(drugId))
				.andExpect(jsonPath("$.distribution.length()", greaterThan(0)))
				.andExpect(jsonPath("$.national.avg").exists())
				.andExpect(jsonPath("$.national.median").exists())
				.andExpect(jsonPath("$.national.min").exists())
				.andExpect(jsonPath("$.national.max").exists());
	}

	@Test
	void 존재하지_않는_약품_통계는_404를_반환한다() throws Exception {
		mockMvc.perform(get("/api/v1/admin/stats/drugs/{drugId}", 9_999_999L)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isNotFound());
	}

	@Test
	void price_gaps의_gapPct가_수기_검산과_일치한다() throws Exception {
		String body = mockMvc.perform(get("/api/v1/admin/stats/price-gaps")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.param("limit", "5"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		java.util.List<java.util.Map<String, Object>> rows = JsonPath.read(body, "$.rows");
		assertThat(rows).isNotEmpty();

		for (java.util.Map<String, Object> row : rows) {
			int cheapAvg = (int) ((java.util.Map<String, Object>) row.get("cheapestRegion")).get("avgPrice");
			int priceyAvg = (int) ((java.util.Map<String, Object>) row.get("priciestRegion")).get("avgPrice");
			double expectedGapPct = Math.round((priceyAvg - cheapAvg) / (double) cheapAvg * 1000) / 10.0;
			double actualGapPct = ((Number) row.get("gapPct")).doubleValue();
			assertThat(actualGapPct).isCloseTo(expectedGapPct, within(0.1));
		}
	}
}
