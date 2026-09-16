package com.pharmaprice.drug.dto;

/** GET /api/v1/drugs/{drugId} 응답 (docs/API.md §3). */
public record DrugDetailResponse(
		long id,
		String itemSeq,
		String displayName,
		String name,
		String maker,
		String category,
		String form,
		String packageUnit,
		String imageUrl,
		PriceStats priceStats) {

	public record PriceStats(
			Integer nationalAvg, Integer nationalMin, Integer nationalMax,
			int pharmacyCount, int reportCount) {
	}
}
