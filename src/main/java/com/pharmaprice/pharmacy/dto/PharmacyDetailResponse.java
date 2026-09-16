package com.pharmaprice.pharmacy.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** GET /api/v1/pharmacies/{pharmacyId} 응답 (docs/API.md §4). */
public record PharmacyDetailResponse(
		long id, String name, String addressRoad, String addressJibun,
		double lat, double lng, String phone,
		Map<String, List<String>> businessHours,
		Double distanceM, RegionRefResponse region,
		List<DrugPriceItem> drugPrices) {

	/** {@code repPrice} 오름차순으로 정렬해 내려준다. */
	public record DrugPriceItem(
			long drugId, String displayName, String packageUnit, String category,
			int repPrice, int minPrice, int maxPrice, int avgPrice,
			int reportCount, LocalDate lastReportedAt,
			Integer nationalAvgPrice, Integer diffFromNationalAvg) {
	}
}
