package com.pharmaprice.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

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

/** docs/ROADMAP.md T-32 완료 판정. */
@AutoConfigureMockMvc
class AdminReportControllerTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	JdbcTemplate jdbcTemplate;

	String adminToken;
	String userToken;
	long userId;

	@BeforeEach
	void setUp() throws Exception {
		adminToken = login("admin@example.com", "Admin1234!");

		String email = "admin-report-test-" + System.nanoTime() + "@example.com";
		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"Password123!","nickname":"제보자"}
								""".formatted(email)))
				.andExpect(status().isCreated());
		String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"Password123!"}
								""".formatted(email)))
				.andReturn().getResponse().getContentAsString();
		userToken = JsonPath.read(loginResponse, "$.accessToken");
		userId = ((Number) JsonPath.read(loginResponse, "$.user.id")).longValue();
	}

	private String login(String email, String password) throws Exception {
		String response = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.accessToken");
	}

	private Map<String, Object> pickExistingStat() {
		return jdbcTemplate.queryForMap(
				"SELECT pharmacy_id, drug_id, rep_price FROM pharmacy_drug_price_stat ORDER BY pharmacy_id LIMIT 1");
	}

	/**
	 * 약품 자체는 전국 중앙값이 존재할 만큼 표본이 많지만(이상치 판정이 정상 동작하도록),
	 * 이 약국·약품 조합만은 아직 통계가 없는 쌍을 고른다 — 새로 제보 몇 건만 추가되는
	 * 상태라 recalculate()의 IQR 트리밍(표본 4건 미만이면 생략)이 결과를 가리지 않는다.
	 */
	private long[] pickPairWithoutExistingStat() {
		String sql = """
				SELECT p.id, d.drug_id
				FROM pharmacy p
				CROSS JOIN (
				    SELECT drug_id FROM pharmacy_drug_price_stat GROUP BY drug_id HAVING COUNT(*) >= 5
				) d
				WHERE p.is_active = true
				  AND NOT EXISTS (
				      SELECT 1 FROM pharmacy_drug_price_stat s
				      WHERE s.pharmacy_id = p.id AND s.drug_id = d.drug_id
				  )
				LIMIT 1
				""";
		return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new long[] {rs.getLong(1), rs.getLong(2)});
	}

	@Test
	void USER_토큰으로_목록과_수정_전부_403이다() throws Exception {
		mockMvc.perform(get("/api/v1/admin/price-reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(patch("/api/v1/admin/price-reports/1")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
						.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void 존재하지_않는_제보를_수정하면_404를_반환한다() throws Exception {
		mockMvc.perform(patch("/api/v1/admin/price-reports/{id}", 9_999_999L)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void 제보를_HIDDEN으로_바꾸면_rep_price가_즉시_재계산되고_DB와_일치한다() throws Exception {
		Map<String, Object> stat = pickExistingStat();
		long pharmacyId = ((Number) stat.get("pharmacy_id")).longValue();
		long drugId = ((Number) stat.get("drug_id")).longValue();
		int repPriceBefore = ((Number) stat.get("rep_price")).intValue();

		// repPriceBefore + 500처럼 이 약국 기준으로는 "약간 다른" 가격도, 이상치 판정은
		// 이 약국이 아니라 약품 전체(전국) 중앙값과 비교하기 때문에(T-26) 이 약국이
		// 원래 비싼 편이면 뜻밖에 이상치로 걸릴 수 있다. 정확히 같은 값을 써서 이
		// 테스트를 "정상 제보가 HIDDEN 처리되는 경우"로 고정한다.
		int newPrice = repPriceBefore;
		String createResponse = mockMvc.perform(post("/api/v1/price-reports")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pharmacyId\":%d,\"drugId\":%d,\"price\":%d}"
								.formatted(pharmacyId, drugId, newPrice)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.flagged").value(false))
				.andReturn().getResponse().getContentAsString();
		long reportId = ((Number) JsonPath.read(createResponse, "$.id")).longValue();
		int reportCountAfterCreate = JsonPath.read(createResponse, "$.updatedStat.reportCount");

		String patchResponse = mockMvc.perform(patch("/api/v1/admin/price-reports/{id}", reportId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"HIDDEN\",\"reason\":\"약국 확인 결과 오기재\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("HIDDEN"))
				.andReturn().getResponse().getContentAsString();

		String statusInDb = jdbcTemplate.queryForObject(
				"SELECT status FROM price_report WHERE id = ?", String.class, reportId);
		assertThat(statusInDb).isEqualTo("HIDDEN");

		int recalculatedReportCount = JsonPath.read(patchResponse, "$.recalculatedStat.reportCount");
		assertThat(recalculatedReportCount).isEqualTo(reportCountAfterCreate - 1);

		int repPriceFromResponse = JsonPath.read(patchResponse, "$.recalculatedStat.repPrice");
		Map<String, Object> statAfter = jdbcTemplate.queryForMap(
				"SELECT rep_price, report_count FROM pharmacy_drug_price_stat WHERE pharmacy_id = ? AND drug_id = ?",
				pharmacyId, drugId);
		assertThat(repPriceFromResponse).isEqualTo(((Number) statAfter.get("rep_price")).intValue());
		assertThat(((Number) statAfter.get("report_count")).intValue()).isEqualTo(reportCountAfterCreate - 1);
	}

	@Test
	void flagged를_false로_풀면_통계에_다시_포함된다() throws Exception {
		long[] pair = pickPairWithoutExistingStat();
		long pharmacyId = pair[0];
		long drugId = pair[1];

		String otherEmail = "admin-report-test-other-" + System.nanoTime() + "@example.com";
		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"Password123!","nickname":"다른제보자"}
								""".formatted(otherEmail)))
				.andExpect(status().isCreated());
		String otherToken = login(otherEmail, "Password123!");

		// 정상 제보 1건으로 이 (약국, 약품) 쌍의 통계를 새로 만든다.
		mockMvc.perform(post("/api/v1/price-reports")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pharmacyId\":%d,\"drugId\":%d,\"price\":3000}".formatted(pharmacyId, drugId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.flagged").value(false))
				.andExpect(jsonPath("$.updatedStat.reportCount").value(1));

		// 전국 중앙값 대비 이상치라 flagged=true로 저장되고 통계에서는 제외된다.
		String createResponse = mockMvc.perform(post("/api/v1/price-reports")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pharmacyId\":%d,\"drugId\":%d,\"price\":200000}".formatted(pharmacyId, drugId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.flagged").value(true))
				.andExpect(jsonPath("$.updatedStat.reportCount").value(1))
				.andReturn().getResponse().getContentAsString();
		long reportId = ((Number) JsonPath.read(createResponse, "$.id")).longValue();

		mockMvc.perform(patch("/api/v1/admin/price-reports/{id}", reportId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"flagged\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.flagged").value(false))
				.andExpect(jsonPath("$.recalculatedStat.reportCount").value(2));

		Boolean flaggedInDb = jdbcTemplate.queryForObject(
				"SELECT flagged FROM price_report WHERE id = ?", Boolean.class, reportId);
		assertThat(flaggedInDb).isFalse();
	}

	@Test
	void REJECTED로_바꾸면_제보자의_reportCount가_1_차감된다() throws Exception {
		Map<String, Object> stat = pickExistingStat();
		long pharmacyId = ((Number) stat.get("pharmacy_id")).longValue();
		long drugId = ((Number) stat.get("drug_id")).longValue();
		int repPrice = ((Number) stat.get("rep_price")).intValue();

		String createResponse = mockMvc.perform(post("/api/v1/price-reports")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pharmacyId\":%d,\"drugId\":%d,\"price\":%d}"
								.formatted(pharmacyId, drugId, repPrice)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long reportId = ((Number) JsonPath.read(createResponse, "$.id")).longValue();

		int reportCountBefore = jdbcTemplate.queryForObject(
				"SELECT report_count FROM app_user WHERE id = ?", Integer.class, userId);
		assertThat(reportCountBefore).isEqualTo(1);

		mockMvc.perform(patch("/api/v1/admin/price-reports/{id}", reportId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"REJECTED\"}"))
				.andExpect(status().isOk());

		int reportCountAfter = jdbcTemplate.queryForObject(
				"SELECT report_count FROM app_user WHERE id = ?", Integer.class, userId);
		assertThat(reportCountAfter).isZero();
	}

	@Test
	void 목록_응답에는_제보자_id와_이메일이_포함된다() throws Exception {
		Map<String, Object> stat = pickExistingStat();
		long pharmacyId = ((Number) stat.get("pharmacy_id")).longValue();
		long drugId = ((Number) stat.get("drug_id")).longValue();
		int repPrice = ((Number) stat.get("rep_price")).intValue();

		mockMvc.perform(post("/api/v1/price-reports")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pharmacyId\":%d,\"drugId\":%d,\"price\":%d}"
								.formatted(pharmacyId, drugId, repPrice)))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/admin/price-reports")
						.param("pharmacyId", String.valueOf(pharmacyId))
						.param("drugId", String.valueOf(drugId))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].reporter.id").exists())
				.andExpect(jsonPath("$.content[0].reporter.email").exists());
	}
}
