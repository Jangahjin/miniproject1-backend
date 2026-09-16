package com.pharmaprice.pharmacy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;

@AutoConfigureMockMvc
class RegionControllerTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	PharmacyRepository pharmacyRepository;

	@Test
	void 시도별로_그룹핑되고_좌표가_모두_채워져있고_pharmacyCount가_실제_약국수와_일치한다() throws Exception {
		String body = mockMvc.perform(get("/api/v1/regions"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "max-age=3600"))
				.andExpect(jsonPath("$.length()", greaterThan(0)))
				.andExpect(jsonPath("$[0].sido").exists())
				.andExpect(jsonPath("$[0].sigungus[0].code").exists())
				.andExpect(jsonPath("$[0].sigungus[0].centerLat").exists())
				.andExpect(jsonPath("$[0].sigungus[0].centerLng").exists())
				.andReturn().getResponse().getContentAsString();

		List<Number> centerLats = JsonPath.read(body, "$..sigungus[*].centerLat");
		List<Number> centerLngs = JsonPath.read(body, "$..sigungus[*].centerLng");
		List<Number> pharmacyCounts = JsonPath.read(body, "$..sigungus[*].pharmacyCount");

		assertThat(centerLats).isNotEmpty();
		centerLats.forEach(lat -> assertThat(lat.doubleValue()).isNotZero());
		centerLngs.forEach(lng -> assertThat(lng.doubleValue()).isNotZero());

		long pharmacyCountSum = pharmacyCounts.stream().mapToLong(Number::longValue).sum();
		assertThat(pharmacyCountSum).isEqualTo(pharmacyRepository.count());
	}
}
