package com.pharmaprice.drug.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;

@AutoConfigureMockMvc
class DrugControllerTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	DrugRepository drugRepository;

	@Test
	void 검색어로_조회하면_200과_1건_이상_그리고_응답필드가_명세와_일치한다() throws Exception {
		mockMvc.perform(get("/api/v1/drugs").param("q", "타이레놀"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()", greaterThan(0)))
				.andExpect(jsonPath("$.content[0].id").exists())
				.andExpect(jsonPath("$.content[0].itemSeq").exists())
				.andExpect(jsonPath("$.content[0].displayName").exists())
				.andExpect(jsonPath("$.content[0].name").exists())
				.andExpect(jsonPath("$.content[0].maker").exists())
				.andExpect(jsonPath("$.content[0].category").exists())
				.andExpect(jsonPath("$.content[0].form").exists())
				.andExpect(jsonPath("$.content[0].packageUnit").exists())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void 전문의약품은_검색결과와_상세조회_어느쪽에도_노출되지_않는다() throws Exception {
		Drug prescriptionOnly = drugRepository.save(Drug.builder()
				.itemSeq("RXTEST0001").name("테스트전문의약품정10밀리그람")
				.displayName("테스트전문의약품 10mg").category("기타").packageUnit("10정")
				.otcFlag(false).build());

		mockMvc.perform(get("/api/v1/drugs").param("q", "테스트전문의약품"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0));

		mockMvc.perform(get("/api/v1/drugs/{id}", prescriptionOnly.getId()))
				.andExpect(status().isNotFound());
	}

	@Test
	void size가_8일때_200ms_이내에_응답한다() throws Exception {
		long startNanos = System.nanoTime();

		mockMvc.perform(get("/api/v1/drugs").param("size", "8"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(8));

		long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
		assertThat(elapsedMs).isLessThan(200);
	}
}
