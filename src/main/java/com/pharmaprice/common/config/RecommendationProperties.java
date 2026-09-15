package com.pharmaprice.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recommendation")
public record RecommendationProperties(
		Weights weights,
		int freshnessHalfLifeDays,
		int priceWindowDays,
		int priceWindowFallbackDays,
		Outlier outlier) {

	public record Weights(double price, double distance, double freshness) {
	}

	public record Outlier(double iqrMultiplier, int minSamples) {
	}
}
