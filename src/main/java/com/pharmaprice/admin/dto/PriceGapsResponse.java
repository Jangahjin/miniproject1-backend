package com.pharmaprice.admin.dto;

import java.util.List;

/** GET /api/v1/admin/stats/price-gaps 응답 (docs/API.md §8, docs/DATABASE.md §5.4). */
public record PriceGapsResponse(List<Row> rows) {

	public record Row(DrugRef drug, RegionAvg cheapestRegion, RegionAvg priciestRegion, int gap, double gapPct) {
	}

	public record DrugRef(long id, String displayName) {
	}

	public record RegionAvg(String sido, String sigungu, int avgPrice) {
	}
}
