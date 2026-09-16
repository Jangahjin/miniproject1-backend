package com.pharmaprice.drug.dto;

/** GET /api/v1/drugs 목록 항목 (docs/API.md §3). */
public record DrugSummaryResponse(
		long id,
		String itemSeq,
		String displayName,
		String name,
		String maker,
		String category,
		String form,
		String packageUnit,
		String imageUrl,
		Integer nationalAvgPrice,
		int pharmacyCount) {
}
