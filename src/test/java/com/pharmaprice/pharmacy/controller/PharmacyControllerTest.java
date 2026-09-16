package com.pharmaprice.pharmacy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.pharmaprice.AbstractIntegrationTest;

/**
 * 실제 시드 데이터(V2/V3, 약국명은 모두 "OO약국" 형태)를 대상으로 검증한다.
 * docs/ROADMAP.md T-19 완료 판정을 그대로 옮겼다.
 */
@AutoConfigureMockMvc
class PharmacyControllerTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	JdbcTemplate jdbcTemplate;

	Long pharmacyIdWithStats;
	Long drugIdWithHistory;

	@BeforeEach
	void setUp() {
		pharmacyIdWithStats = jdbcTemplate.queryForObject(
				"SELECT pharmacy_id FROM pharmacy_drug_price_stat ORDER BY pharmacy_id LIMIT 1", Long.class);
		drugIdWithHistory = jdbcTemplate.queryForObject(
				"SELECT drug_id FROM pharmacy_drug_price_stat WHERE pharmacy_id = ? ORDER BY drug_id LIMIT 1",
				Long.class, pharmacyIdWithStats);
	}

	@Test
	void 이름으로_검색하면_200과_1건_이상_그리고_거리는_null이다() throws Exception {
		String body = mockMvc.perform(get("/api/v1/pharmacies").param("q", "약국"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()", greaterThan(0)))
				.andExpect(jsonPath("$.content[0].id").exists())
				.andExpect(jsonPath("$.content[0].name").exists())
				.andExpect(jsonPath("$.content[0].region.code").exists())
				.andReturn().getResponse().getContentAsString();

		assertThat((Object) JsonPath.read(body, "$.content[0].distanceM")).isNull();
	}

	@Test
	void 좌표로_검색하면_거리가_채워진다() throws Exception {
		mockMvc.perform(get("/api/v1/pharmacies")
						.param("lat", "37.4979").param("lng", "127.0276").param("radius", "2000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()", greaterThan(0)))
				.andExpect(jsonPath("$.content[0].distanceM").exists());
	}

	@Test
	void q도_좌표도_없으면_400을_반환한다() throws Exception {
		mockMvc.perform(get("/api/v1/pharmacies"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 대한민국_범위_밖_좌표는_400을_반환한다() throws Exception {
		mockMvc.perform(get("/api/v1/pharmacies").param("lat", "10.0").param("lng", "10.0"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 상세조회시_drugPrices가_repPrice_오름차순이고_diffFromNationalAvg가_계산된다() throws Exception {
		String body = mockMvc.perform(get("/api/v1/pharmacies/{id}", pharmacyIdWithStats))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(pharmacyIdWithStats))
				.andExpect(jsonPath("$.businessHours").exists())
				.andExpect(jsonPath("$.region.sido").exists())
				.andExpect(jsonPath("$.drugPrices.length()", greaterThan(0)))
				.andReturn().getResponse().getContentAsString();

		List<Integer> prices = JsonPath.read(body, "$.drugPrices[*].repPrice");
		assertThat(prices).isEqualTo(prices.stream().sorted().toList());

		int repPrice = JsonPath.read(body, "$.drugPrices[0].repPrice");
		int nationalAvg = JsonPath.read(body, "$.drugPrices[0].nationalAvgPrice");
		int diff = JsonPath.read(body, "$.drugPrices[0].diffFromNationalAvg");
		assertThat(diff).isEqualTo(repPrice - nationalAvg);
	}

	@Test
	void 존재하지_않는_약국은_404를_반환한다() throws Exception {
		mockMvc.perform(get("/api/v1/pharmacies/{id}", 9_999_999L))
				.andExpect(status().isNotFound());
	}

	@Test
	void 가격_이력_응답에_flagged_항목이_포함된다() throws Exception {
		mockMvc.perform(get(
						"/api/v1/pharmacies/{pharmacyId}/drugs/{drugId}/history",
						pharmacyIdWithStats, drugIdWithHistory))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pharmacyId").value(pharmacyIdWithStats))
				.andExpect(jsonPath("$.drugId").value(drugIdWithHistory))
				.andExpect(jsonPath("$.points.length()", greaterThan(0)))
				.andExpect(jsonPath("$.points[0].flagged").exists());
	}

	@Test
	void days를_좁히면_결과가_줄어든다() throws Exception {
		String wide = mockMvc.perform(get(
						"/api/v1/pharmacies/{pharmacyId}/drugs/{drugId}/history",
						pharmacyIdWithStats, drugIdWithHistory).param("days", "365"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String narrow = mockMvc.perform(get(
						"/api/v1/pharmacies/{pharmacyId}/drugs/{drugId}/history",
						pharmacyIdWithStats, drugIdWithHistory).param("days", "1"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		List<Object> widePoints = JsonPath.read(wide, "$.points");
		List<Object> narrowPoints = JsonPath.read(narrow, "$.points");
		assertThat(narrowPoints.size()).isLessThan(widePoints.size());
	}

	@Test
	void 이력이_없어도_빈_배열과_200을_반환한다() throws Exception {
		mockMvc.perform(get(
						"/api/v1/pharmacies/{pharmacyId}/drugs/{drugId}/history",
						9_999_999L, 9_999_999L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.points.length()").value(0));
	}
}
