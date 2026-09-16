package com.pharmaprice.admin.dto;

import java.util.List;

import com.pharmaprice.pharmacy.dto.RegionRefResponse;

/** GET /api/v1/admin/stats/regions 응답 (docs/API.md §8, docs/DATABASE.md §5.3). */
public record RegionStatsResponse(List<Row> rows) {

	public record Row(
			RegionRefResponse region, DrugRef drug, int avgPrice, int minPrice, int maxPrice,
			long pharmacyCount, long reportCount) {
	}

	public record DrugRef(long id, String displayName) {
	}
}
