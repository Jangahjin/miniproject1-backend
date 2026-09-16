package com.pharmaprice.admin.dto;

import java.time.LocalDate;
import java.util.List;

/** GET /api/v1/admin/stats/overview 응답 (docs/API.md §8). */
public record AdminOverviewResponse(
		Totals totals, List<TrendPoint> recentTrend, long flaggedReportCount, double coverageRate) {

	public record Totals(
			long pharmacyCount, long drugCount, long reportCount, long userCount, long coveredPairCount) {
	}

	public record TrendPoint(LocalDate date, long reportCount) {
	}
}
