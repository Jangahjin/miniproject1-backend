package com.pharmaprice.pharmacy.dto;

/** GET /api/v1/pharmacies 목록 항목 (docs/API.md §4). */
public record PharmacySummaryResponse(
		long id, String name, String addressRoad, double lat, double lng, String phone,
		Double distanceM, RegionRefResponse region) {
}
