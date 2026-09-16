package com.pharmaprice.recommendation.dto;

import java.time.LocalDate;
import java.util.List;

/** GET /api/v1/search 응답 (docs/API.md §5). */
public record SearchResponse(
		DrugSummary drug,
		QueryEcho query,
		Summary summary,
		String dataSource,
		List<ResultItem> results,
		Suggestion suggestion) {

	public record DrugSummary(long id, String displayName, String packageUnit, String imageUrl) {
	}

	public record QueryEcho(double lat, double lng, int radius, String sort, String locationSource) {
	}

	public record Summary(
			int resultCount, Integer candidateAvgPrice, Integer candidateMinPrice,
			Integer candidateMaxPrice, Integer maxSaving) {
	}

	public record ResultItem(
			int rank, boolean recommended, PharmacySummary pharmacy, PriceInfo price,
			double distanceM, double score, ScoreBreakdownResponse scoreBreakdown, List<String> badges) {
	}

	public record PharmacySummary(long id, String name, String addressRoad, double lat, double lng, String phone) {
	}

	public record PriceInfo(
			int repPrice, int minPrice, int avgPrice, int savingVsCandidateAvg,
			int reportCount, LocalDate lastReportedAt, long daysSinceLastReport) {
	}

	public record ScoreBreakdownResponse(
			double priceScore, double distanceScore, double freshnessScore, Weights weights) {
	}

	public record Weights(double price, double distance, double freshness) {
	}

	/** 결과 0건일 때만 채워진다 (docs/PRD.md F3-9). */
	public record Suggestion(String type, int recommendedRadius, long estimatedCount) {
	}
}
