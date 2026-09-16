package com.pharmaprice.report.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;

/** docs/ROADMAP.md T-26, T-28 완료 판정. */
@AutoConfigureMockMvc
class PriceReportControllerTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	JdbcTemplate jdbcTemplate;
	@Autowired
	DrugRepository drugRepository;

	String accessToken;

	@BeforeEach
	void setUp() throws Exception {
		String email = "report-test-" + System.nanoTime() + "@example.com";
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
		accessToken = JsonPath.read(loginResponse, "$.accessToken");
	}

	private Map<String, Object> pickExistingStat() {
		return jdbcTemplate.queryForMap(
				"SELECT pharmacy_id, drug_id, rep_price FROM pharmacy_drug_price_stat ORDER BY pharmacy_id LIMIT 1");
	}

	@Test
	void 정상_제보하면_201과_updatedStat이_반영되고_search에_즉시_반영된다() throws Exception {
		Map<String, Object> stat = pickExistingStat();
		long pharmacyId = ((Number) stat.get("pharmacy_id")).longValue();
		long drugId = ((Number) stat.get("drug_id")).longValue();
		int repPrice = ((Number) stat.get("rep_price")).intValue();

		Integer countBefore = jdbcTemplate.queryForObject(
				"SELECT report_count FROM pharmacy_drug_price_stat WHERE pharmacy_id = ? AND drug_id = ?",
				Integer.class, pharmacyId, drugId);

		mockMvc.perform(post("/api/v1/price-reports")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pharmacyId\":%d,\"drugId\":%d,\"price\":%d}"
								.formatted(pharmacyId, drugId, repPrice)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.flagged").value(false))
				.andExpect(jsonPath("$.updatedStat.reportCount").value(countBefore + 1));

		Map<String, Object> pharmacy = jdbcTemplate.queryForMap(
				"SELECT lat, lng FROM pharmacy WHERE id = ?", pharmacyId);

		mockMvc.perform(get("/api/v1/search")
						.param("drugId", String.valueOf(drugId))
						.param("lat", pharmacy.get("lat").toString())
						.param("lng", pharmacy.get("lng").toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.resultCount", greaterThan(0)))
				.andExpect(jsonPath("$.results[?(@.pharmacy.id == " + pharmacyId + ")]").exists());
	}

	@Test
	void 같은_날_같은_약국_약품에_재제보하면_409를_반환한다() throws Exception {
		Map<String, Object> stat = pickExistingStat();
		long pharmacyId = ((Number) stat.get("pharmacy_id")).longValue();
		long drugId = ((Number) stat.get("drug_id")).longValue();
		int repPrice = ((Number) stat.get("rep_price")).intValue();
		String body = "{\"pharmacyId\":%d,\"drugId\":%d,\"price\":%d}".formatted(pharmacyId, drugId, repPrice);

		mockMvc.perform(post("/api/v1/price-reports")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/price-reports")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_REPORT"))
				.andExpect(jsonPath("$.message").value("오늘 이미 이 약국의 해당 약품 가격을 제보하셨습니다."));
	}

	@Test
	void 이상치_제보는_201과_flagged_true_warning을_반환하고_repPrice는_바뀌지_않는다() throws Exception {
		Map<String, Object> stat = pickExistingStat();
		long pharmacyId = ((Number) stat.get("pharmacy_id")).longValue();
		long drugId = ((Number) stat.get("drug_id")).longValue();
		int repPriceBefore = ((Number) stat.get("rep_price")).intValue();

		mockMvc.perform(post("/api/v1/price-reports")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pharmacyId\":%d,\"drugId\":%d,\"price\":200000}"
								.formatted(pharmacyId, drugId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.flagged").value(true))
				.andExpect(jsonPath("$.flagReason").value("OUTLIER_HIGH"))
				.andExpect(jsonPath("$.warning").exists())
				.andExpect(jsonPath("$.updatedStat.repPrice").value(repPriceBefore));
	}

	@Test
	void 전문의약품을_제보하면_422를_반환한다() throws Exception {
		Drug prescriptionDrug = drugRepository.save(Drug.builder()
				.name("전문의약품테스트").displayName("전문의약품테스트").category("기타")
				.packageUnit("1정").otcFlag(false).build());
		long pharmacyId = ((Number) pickExistingStat().get("pharmacy_id")).longValue();

		mockMvc.perform(post("/api/v1/price-reports")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pharmacyId\":%d,\"drugId\":%d,\"price\":3000}"
								.formatted(pharmacyId, prescriptionDrug.getId())))
				.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void 미래_구매일로_제보하면_400을_반환한다() throws Exception {
		Map<String, Object> stat = pickExistingStat();
		long pharmacyId = ((Number) stat.get("pharmacy_id")).longValue();
		long drugId = ((Number) stat.get("drug_id")).longValue();

		mockMvc.perform(post("/api/v1/price-reports")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pharmacyId\":%d,\"drugId\":%d,\"price\":3000,\"purchasedAt\":\"2099-01-01\"}"
								.formatted(pharmacyId, drugId)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 제보_후_내정보의_reportCount가_1_증가한다() throws Exception {
		Map<String, Object> stat = pickExistingStat();
		long pharmacyId = ((Number) stat.get("pharmacy_id")).longValue();
		long drugId = ((Number) stat.get("drug_id")).longValue();
		int repPrice = ((Number) stat.get("rep_price")).intValue();

		int reportCountBefore = JsonPath.read(
				mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
						.andReturn().getResponse().getContentAsString(),
				"$.reportCount");
		assertThat(reportCountBefore).isZero();

		mockMvc.perform(post("/api/v1/price-reports")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pharmacyId\":%d,\"drugId\":%d,\"price\":%d}"
								.formatted(pharmacyId, drugId, repPrice)))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(jsonPath("$.reportCount").value(1));
	}

	@Test
	void 목록_응답에_제보자_이메일과_id가_없다() throws Exception {
		Map<String, Object> stat = pickExistingStat();
		long pharmacyId = ((Number) stat.get("pharmacy_id")).longValue();
		long drugId = ((Number) stat.get("drug_id")).longValue();
		int repPrice = ((Number) stat.get("rep_price")).intValue();

		mockMvc.perform(post("/api/v1/price-reports")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pharmacyId\":%d,\"drugId\":%d,\"price\":%d}"
								.formatted(pharmacyId, drugId, repPrice)))
				.andExpect(status().isCreated());

		String listResponse = mockMvc.perform(get("/api/v1/price-reports")
						.param("pharmacyId", String.valueOf(pharmacyId))
						.param("drugId", String.valueOf(drugId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].reporter.nickname").value("제보자"))
				.andExpect(jsonPath("$.content[0].hasReceipt").value(false))
				.andReturn().getResponse().getContentAsString();

		assertThat(listResponse).doesNotContain("email").doesNotContain("\"userId\"").doesNotContain("receiptFileId");
	}

	@Test
	void mine을_비로그인으로_호출하면_401을_반환한다() throws Exception {
		mockMvc.perform(get("/api/v1/price-reports").param("mine", "true"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void mine을_로그인_상태로_호출하면_본인_제보만_보인다() throws Exception {
		Map<String, Object> stat = pickExistingStat();
		long pharmacyId = ((Number) stat.get("pharmacy_id")).longValue();
		long drugId = ((Number) stat.get("drug_id")).longValue();
		int repPrice = ((Number) stat.get("rep_price")).intValue();

		mockMvc.perform(post("/api/v1/price-reports")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pharmacyId\":%d,\"drugId\":%d,\"price\":%d}"
								.formatted(pharmacyId, drugId, repPrice)))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/price-reports")
						.param("mine", "true")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()", greaterThan(0)));
	}
}
