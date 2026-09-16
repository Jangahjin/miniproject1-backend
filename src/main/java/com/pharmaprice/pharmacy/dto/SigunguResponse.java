package com.pharmaprice.pharmacy.dto;

/** GET /api/v1/regions 응답의 시군구 항목 (docs/API.md §7). */
public record SigunguResponse(
		String code, String sigungu, double centerLat, double centerLng, long pharmacyCount) {
}
