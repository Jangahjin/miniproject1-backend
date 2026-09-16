package com.pharmaprice.pharmacy.dto;

import java.time.LocalDate;
import java.util.List;

/** GET /api/v1/pharmacies/{pharmacyId}/drugs/{drugId}/history 응답 (docs/API.md §4, T-20). */
public record PriceHistoryResponse(long pharmacyId, long drugId, List<PricePoint> points) {

	/** {@code purchasedAt} 오름차순으로 정렬해 내려준다. */
	public record PricePoint(LocalDate purchasedAt, int price, boolean flagged) {
	}
}
