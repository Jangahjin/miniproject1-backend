package com.pharmaprice.recommendation.controller;

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
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.drug.repository.DrugRepository;

/**
 * 실제 시드 데이터(V2/V3, 424개 약국·35,000여 건 제보)를 대상으로 검증한다.
 * docs/ROADMAP.md T-15 완료 판정을 그대로 옮겼다.
 */
@AutoConfigureMockMvc
class SearchControllerTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	DrugRepository drugRepository;

	Long drugId;

	@BeforeEach
	void setUp() {
		drugId = drugRepository.findAll().stream()
				.filter(d -> "SYNDRUG0001".equals(d.getItemSeq()))
				.findFirst().orElseThrow().getId();
	}

	@Test
	void 응답_구조가_명세와_필드단위로_일치한다() throws Exception {
		mockMvc.perform(get("/api/v1/search")
						.param("drugId", String.valueOf(drugId))
						.param("lat", "37.4979").param("lng", "127.0276").param("radius", "2000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.drug.id").value(drugId))
				.andExpect(jsonPath("$.query.locationSource").value("GPS"))
				.andExpect(jsonPath("$.summary.resultCount", greaterThan(0)))
				.andExpect(jsonPath("$.dataSource").exists())
				.andExpect(jsonPath("$.results[0].rank").value(1))
				.andExpect(jsonPath("$.results[0].recommended").value(true))
				.andExpect(jsonPath("$.results[0].scoreBreakdown.priceScore").exists())
				.andExpect(jsonPath("$.results[0].scoreBreakdown.weights.price").value(0.6))
				.andExpect(jsonPath("$.results[0].badges").isArray());
	}

	@Test
	void sort을_PRICE로_바꾸면_순위가_실제로_달라진다() throws Exception {
		String scoreBody = mockMvc.perform(get("/api/v1/search")
						.param("drugId", String.valueOf(drugId))
						.param("lat", "37.4979").param("lng", "127.0276").param("radius", "2000"))
				.andReturn().getResponse().getContentAsString();
		String priceBody = mockMvc.perform(get("/api/v1/search")
						.param("drugId", String.valueOf(drugId))
						.param("lat", "37.4979").param("lng", "127.0276").param("radius", "2000")
						.param("sort", "PRICE"))
				.andReturn().getResponse().getContentAsString();

		List<Object> scoreOrder = JsonPath.read(scoreBody, "$.results[*].pharmacy.id");
		List<Object> priceOrder = JsonPath.read(priceBody, "$.results[*].pharmacy.id");

		assertThat(priceOrder).isNotEqualTo(scoreOrder);
	}

	@Test
	void regionCode_만으로도_검색된다() throws Exception {
		mockMvc.perform(get("/api/v1/search")
						.param("drugId", String.valueOf(drugId))
						.param("regionCode", "11680"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.query.locationSource").value("REGION"))
				.andExpect(jsonPath("$.summary.resultCount", greaterThan(0)));
	}

	@Test
	void 결과가_없으면_suggestion의_estimatedCount가_실제_확대반경_건수와_일치한다() throws Exception {
		String zeroBody = mockMvc.perform(get("/api/v1/search")
						.param("drugId", String.valueOf(drugId))
						.param("lat", "33.5").param("lng", "126.5").param("radius", "500")) // 제주 — 시드 데이터가 없는 지역
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.resultCount").value(0))
				.andExpect(jsonPath("$.suggestion.type").value("EXPAND_RADIUS"))
				.andExpect(jsonPath("$.suggestion.recommendedRadius").value(1000))
				.andReturn().getResponse().getContentAsString();

		int estimatedCount = JsonPath.read(zeroBody, "$.suggestion.estimatedCount");

		String expandedBody = mockMvc.perform(get("/api/v1/search")
						.param("drugId", String.valueOf(drugId))
						.param("lat", "33.5").param("lng", "126.5").param("radius", "1000"))
				.andReturn().getResponse().getContentAsString();
		int actualCount = JsonPath.read(expandedBody, "$.summary.resultCount");

		assertThat(estimatedCount).isEqualTo(actualCount);
	}

	@Test
	void 좌표와_지역코드가_모두_없으면_400을_반환한다() throws Exception {
		mockMvc.perform(get("/api/v1/search").param("drugId", String.valueOf(drugId)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 응답시간이_충분히_빠르다() throws Exception {
		long startNanos = System.nanoTime();

		mockMvc.perform(get("/api/v1/search")
						.param("drugId", String.valueOf(drugId))
						.param("lat", "37.4979").param("lng", "127.0276").param("radius", "2000"))
				.andExpect(status().isOk());

		long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
		assertThat(elapsedMs).isLessThan(500);
	}
}
