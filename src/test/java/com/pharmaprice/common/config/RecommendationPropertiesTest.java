package com.pharmaprice.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RecommendationPropertiesTest {

	@Autowired
	RecommendationProperties properties;

	@Test
	void bindsWeightsFromYml() {
		assertThat(properties.weights().price()).isEqualTo(0.60);
		assertThat(properties.weights().distance()).isEqualTo(0.25);
		assertThat(properties.weights().freshness()).isEqualTo(0.15);
	}

	@Test
	void bindsRemainingRecommendationSettingsFromYml() {
		assertThat(properties.freshnessHalfLifeDays()).isEqualTo(30);
		assertThat(properties.priceWindowDays()).isEqualTo(90);
		assertThat(properties.priceWindowFallbackDays()).isEqualTo(180);
		assertThat(properties.outlier().iqrMultiplier()).isEqualTo(1.5);
		assertThat(properties.outlier().minSamples()).isEqualTo(4);
	}
}
